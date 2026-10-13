package com.parosurvivors.serviya.users.application.services;

import com.parosurvivors.serviya.notifications.application.ports.input.NotificationServicePort;
import com.parosurvivors.serviya.notifications.domain.ChannelName;
import com.parosurvivors.serviya.shared.exceptions.InvalidStateException;
import com.parosurvivors.serviya.shared.exceptions.UnauthorizedException;
import com.parosurvivors.serviya.users.application.dto.command.ConfirmPasswordResetCommand;
import com.parosurvivors.serviya.users.application.dto.command.CreateUserAccountCommand;
import com.parosurvivors.serviya.users.application.dto.command.LoginCommand;
import com.parosurvivors.serviya.users.application.dto.command.RegisterUserCommand;
import com.parosurvivors.serviya.users.application.dto.command.RequestPasswordResetCommand;
import com.parosurvivors.serviya.users.application.dto.result.AuthResult;
import com.parosurvivors.serviya.users.application.dto.result.IssuedResetToken;
import com.parosurvivors.serviya.users.application.dto.result.IssuedToken;
import com.parosurvivors.serviya.users.application.dto.result.TokenValidationResult;
import com.parosurvivors.serviya.users.application.ports.input.PasswordResetTokenServicePort;
import com.parosurvivors.serviya.users.application.ports.input.UserAuthenticationServicePort;
import com.parosurvivors.serviya.users.application.ports.input.UserCreationServicePort;
import com.parosurvivors.serviya.users.application.ports.input.UserRoleServicePort;
import com.parosurvivors.serviya.users.application.ports.input.UserServicePort;
import com.parosurvivors.serviya.users.application.ports.output.ResetLinkPort;
import com.parosurvivors.serviya.users.application.ports.output.TokenProviderPort;
import com.parosurvivors.serviya.users.application.ports.output.UserPersistencePort;
import com.parosurvivors.serviya.users.application.ports.output.UserReadPort;
import com.parosurvivors.serviya.users.domain.PasswordResetToken;
import com.parosurvivors.serviya.users.domain.Role;
import com.parosurvivors.serviya.users.domain.RoleName;
import com.parosurvivors.serviya.users.domain.User;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orquestador de autenticacion (RF-001 login, RF-002 registro). El registro delega la creacion
 * comun en {@link UserCreationServicePort}. La emision del token va por {@link TokenProviderPort}
 * (detalle JWT en infraestructura). Recuperacion de contrasena sigue pendiente (placeholder).
 */
@Component
@RequiredArgsConstructor
public class UserAuthenticationService implements UserAuthenticationServicePort {

    private final UserServicePort userServicePort;
    private final UserCreationServicePort userCreationServicePort;
    private final PasswordResetTokenServicePort passwordResetTokenServicePort;
    private final UserReadPort userReadPort;
    private final UserPersistencePort userPersistencePort;
    private final NotificationServicePort notificationServicePort;
    private final UserRoleServicePort userRoleServicePort;
    private final PasswordEncoder passwordEncoder;
    private final TokenProviderPort tokenProvider;
    private final ResetLinkPort resetLinkPort;

    @Override
    public AuthResult login(LoginCommand command) {
        // Mensaje generico para no revelar si el email existe.
        User user = userReadPort.findByEmail(command.email())
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));

        if (!passwordEncoder.matches(command.password(), user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid email or password");
        }
        // RF-001 / RNF-007: cuentas baneadas o eliminadas no pueden iniciar sesion.
        if (user.isBanned()) {
            throw new UnauthorizedException("Account is banned");
        }
        if (user.isDeleted()) {
            throw new UnauthorizedException("Account no longer exists");
        }

        List<RoleName> roles = userRoleServicePort.getUserRoles(user.getId()).stream()
                .map(Role::getName)
                .toList();

        return toAuthResult(user.getId(), roles);
    }

    @Override
    public AuthResult register(RegisterUserCommand command) {
        // Restricción de POLÍTICA del registro público: solo CLIENT u OFFERER, nunca ADMIN. El mecanismo
        // interno de creación (createUserAccount) no restringe roles; la política vive en este llamador.
        requirePublicRole(command.role());
        CreateUserAccountCommand accountCommand = new CreateUserAccountCommand(
                command.email(),
                command.password(),
                command.fullName(),
                command.role(),
                command.documentType(),
                command.documentNumber(),
                command.phone(),
                command.acceptedTerms(),
                command.addressLine(),
                command.city(),
                command.latitude(),
                command.longitude());

        User created = userCreationServicePort.createUserAccount(accountCommand);
        // Bienvenida al registro por doble canal (interno + email).
        notificationServicePort.notify(
                created.getId(),
                "welcome",
                "¡Bienvenido a ServiYa!",
                "Tu cuenta fue creada correctamente. Completa tu perfil y empieza a usar ServiYa.",
                "USER",
                created.getId(),
                Set.of(ChannelName.INTERNAL, ChannelName.EMAIL),
                Map.of());
        return toAuthResult(created.getId(), created.getRoles());
    }

    /**
     * RF-010/011: adquiere el rol y re-emite el JWT con los roles ya actualizados.
     *
     * <p>Atomica: la asignacion del rol y la inicializacion de sus filas 1-a-1 (perfil de oferente,
     * metricas — que reaccionan a RoleAssignedEvent en BEFORE_COMMIT) ocurren en esta transaccion.
     * El token se emite despues de releer los roles, por lo que el usuario obtiene acceso inmediato
     * sin volver a iniciar sesion.</p>
     */
    @Override
    @Transactional
    public AuthResult acquireRole(Long userId, String roleName) {
        userRoleServicePort.acquireRole(userId, roleName);

        List<RoleName> roles = userRoleServicePort.getUserRoles(userId).stream()
                .map(Role::getName)
                .toList();

        // Aviso de rol recién adquirido por el propio usuario (canal interno).
        notificationServicePort.notify(
                userId,
                "role_acquired",
                "Nuevo rol activado",
                "Ahora tienes el rol " + roleName + " activo en tu cuenta de ServiYa.",
                "USER",
                userId,
                null,
                Map.of());

        return toAuthResult(userId, roles);
    }

    /**
     * RF-003, paso 1: emite el enlace de recuperación y lo envía por correo.
     *
     * <p><b>Nunca revela si el correo está registrado.</b> El llamador recibe la misma respuesta exista
     * o no la cuenta; distinguirlas convertiría este formulario público en un oráculo para averiguar qué
     * correos tienen cuenta (materia prima de phishing y credential stuffing). El texto idéntico no basta
     * por sí solo: el <i>tiempo</i> también delata, y por eso importa que el envío del correo no ocurra
     * en línea — el módulo de notificaciones lo dispara AFTER_COMMIT, así que la respuesta al navegador
     * no espera a la llamada a Brevo en ninguno de los dos casos.</p>
     *
     * <p>Las cuentas baneadas o eliminadas se ignoran en silencio, por el mismo motivo: no pueden iniciar
     * sesión, y responder distinto delataría su estado.</p>
     */
    @Override
    @Transactional
    public void requestPasswordReset(RequestPasswordResetCommand command) {
        userReadPort.findByEmail(command.email())
                .filter(user -> !user.isBanned() && !user.isDeleted())
                .ifPresent(this::sendPasswordResetLink);
    }

    /**
     * RF-003, paso intermedio: ¿el enlace sigue sirviendo? Solo lectura — NO consume el token, porque
     * algunos clientes de correo previsualizan los enlaces y lo quemarían antes de que el usuario llegue
     * al formulario.
     *
     * <p>El resultado detallado se colapsa aquí a "sirve / no sirve": hacia fuera nunca se distingue
     * entre inexistente, expirado y ya usado.</p>
     */
    @Override
    public void validatePasswordResetToken(String rawToken) {
        if (passwordResetTokenServicePort.validateToken(rawToken) != TokenValidationResult.VALID) {
            throw new InvalidStateException(PasswordResetTokenServicePort.GENERIC_INVALID_TOKEN_MESSAGE);
        }
    }

    /**
     * RF-003, paso 2: valida el token, establece la contraseña nueva y contiene el daño.
     *
     * <p>Todo en una transacción: si algo falla después de consumir el token, el consumo se revierte y el
     * enlace sigue sirviendo. El {@code userId} sale del token encontrado en la tabla, JAMÁS de la
     * petición — es lo que impide cambiar la contraseña de otra cuenta.</p>
     */
    @Override
    @Transactional
    public void confirmPasswordReset(ConfirmPasswordResetCommand command) {
        // Valida, quema este token y el resto de los del usuario. Lanza InvalidStateException (409)
        // con un mensaje genérico si no existe, expiró o ya se usó.
        PasswordResetToken token = passwordResetTokenServicePort.consumeToken(command.token());

        User user = userReadPort.findById(token.getUserId())
                .filter(candidate -> !candidate.isBanned() && !candidate.isDeleted())
                .orElseThrow(() -> new InvalidStateException(
                        PasswordResetTokenServicePort.GENERIC_INVALID_TOKEN_MESSAGE));

        user.changePassword(passwordEncoder.encode(command.newPassword()));
        userPersistencePort.update(user);

        // Aviso de contención: si el cambio no lo hizo el titular, este correo es su señal de alarma.
        notificationServicePort.notify(
                user.getId(),
                "password_changed",
                "Tu contraseña fue actualizada",
                "La contraseña de tu cuenta de ServiYa acaba de cambiarse. Si no fuiste tú, "
                        + "restablecela de inmediato y contacta con soporte.",
                "USER",
                user.getId(),
                Set.of(ChannelName.INTERNAL, ChannelName.EMAIL),
                Map.of());
    }

    /** Emite el token, arma el enlace (URL de configuración del backend) y encola el correo. */
    private void sendPasswordResetLink(User user) {
        IssuedResetToken issued = passwordResetTokenServicePort.createToken(user.getId());
        String resetLink = resetLinkPort.buildResetLink(issued.rawToken());
        long minutesValid = Math.max(1, Duration.between(LocalDateTime.now(), issued.expiresAt()).toMinutes());

        // Solo EMAIL: el usuario no puede iniciar sesión, así que una notificación interna no le llegaría.
        // El enlace viaja en protectedData, que NO se persiste — el token nunca toca la tabla notifications.
        notificationServicePort.notify(
                user.getId(),
                "password_reset",
                "Restablece tu contraseña",
                "Recibimos una solicitud para restablecer la contraseña de tu cuenta de ServiYa. "
                        + "El enlace vence en " + minutesValid + " minutos y sirve una sola vez. "
                        + "Si no lo solicitaste, ignora este correo: tu contraseña no cambiará.",
                "USER",
                user.getId(),
                Set.of(ChannelName.EMAIL),
                Map.of("actionUrl", resetLink, "actionLabel", "Restablecer mi contraseña"));
    }

    /** El registro público solo permite roles CLIENT u OFFERER; ADMIN nunca por esta vía. */
    private void requirePublicRole(String role) {
        RoleName roleName;
        try {
            roleName = RoleName.valueOf(role == null ? "" : role.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new InvalidStateException("Invalid role: " + role);
        }
        if (roleName == RoleName.ADMIN) {
            throw new InvalidStateException("Public registration cannot create ADMIN accounts");
        }
    }

    private AuthResult toAuthResult(Long userId, List<RoleName> roles) {
        IssuedToken issued = tokenProvider.issue(userId, roles);
        return new AuthResult(issued.token(), userId, roles, issued.expiresAt());
    }
}
