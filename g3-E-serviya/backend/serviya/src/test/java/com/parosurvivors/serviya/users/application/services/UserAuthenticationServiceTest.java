package com.parosurvivors.serviya.users.application.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.parosurvivors.serviya.notifications.application.ports.input.NotificationServicePort;
import com.parosurvivors.serviya.shared.exceptions.InvalidStateException;
import com.parosurvivors.serviya.shared.exceptions.UnauthorizedException;
import com.parosurvivors.serviya.users.application.dto.command.LoginCommand;
import com.parosurvivors.serviya.users.application.dto.command.RegisterUserCommand;
import com.parosurvivors.serviya.users.application.dto.result.AuthResult;
import com.parosurvivors.serviya.users.application.dto.result.IssuedToken;
import com.parosurvivors.serviya.users.domain.RoleName;
import com.parosurvivors.serviya.users.application.ports.input.PasswordResetTokenServicePort;
import com.parosurvivors.serviya.users.application.ports.input.UserCreationServicePort;
import com.parosurvivors.serviya.users.application.ports.input.UserRoleServicePort;
import com.parosurvivors.serviya.users.application.ports.input.UserServicePort;
import com.parosurvivors.serviya.users.application.ports.output.ResetLinkPort;
import com.parosurvivors.serviya.users.application.ports.output.TokenProviderPort;
import com.parosurvivors.serviya.users.application.ports.output.UserPersistencePort;
import com.parosurvivors.serviya.users.application.ports.output.UserReadPort;
import com.parosurvivors.serviya.users.application.dto.command.ConfirmPasswordResetCommand;
import com.parosurvivors.serviya.users.application.dto.command.RequestPasswordResetCommand;
import com.parosurvivors.serviya.users.application.dto.result.IssuedResetToken;
import com.parosurvivors.serviya.users.application.dto.result.TokenValidationResult;
import com.parosurvivors.serviya.users.domain.PasswordResetToken;
import com.parosurvivors.serviya.users.domain.User;
import com.parosurvivors.serviya.notifications.domain.ChannelName;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Reglas de HU-001 (login): valida bcrypt y rechaza cuentas baneadas o eliminadas.
 */
@ExtendWith(MockitoExtension.class)
class UserAuthenticationServiceTest {

    @Mock UserServicePort userServicePort;
    @Mock UserCreationServicePort userCreationServicePort;
    @Mock PasswordResetTokenServicePort passwordResetTokenServicePort;
    @Mock UserReadPort userReadPort;
    @Mock UserPersistencePort userPersistencePort;
    @Mock NotificationServicePort notificationServicePort;
    @Mock UserRoleServicePort userRoleServicePort;
    @Mock PasswordEncoder passwordEncoder;
    @Mock TokenProviderPort tokenProvider;
    @Mock ResetLinkPort resetLinkPort;

    @InjectMocks UserAuthenticationService service;

    private final LoginCommand command = new LoginCommand("user@example.com", "raw-password");

    private User activeUser() {
        return User.builder().id(1L).email("user@example.com").passwordHash("$2a$hash").banned(false).build();
    }

    @Test
    void login_succeeds_withValidCredentials() {
        when(userReadPort.findByEmail("user@example.com")).thenReturn(Optional.of(activeUser()));
        when(passwordEncoder.matches("raw-password", "$2a$hash")).thenReturn(true);
        when(userRoleServicePort.getUserRoles(1L)).thenReturn(List.of());
        when(tokenProvider.issue(anyLong(), anyList()))
                .thenReturn(new IssuedToken("jwt-token", LocalDateTime.now().plusHours(1)));

        AuthResult result = service.login(command);

        assertThat(result.token()).isEqualTo("jwt-token");
        assertThat(result.userId()).isEqualTo(1L);
    }

    @Test
    void login_fails_whenEmailNotFound() {
        when(userReadPort.findByEmail("user@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(command)).isInstanceOf(UnauthorizedException.class);
        verify(tokenProvider, never()).issue(anyLong(), anyList());
    }

    @Test
    void login_fails_whenPasswordDoesNotMatch() {
        when(userReadPort.findByEmail("user@example.com")).thenReturn(Optional.of(activeUser()));
        when(passwordEncoder.matches("raw-password", "$2a$hash")).thenReturn(false);

        assertThatThrownBy(() -> service.login(command)).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void login_fails_whenUserIsBanned() {
        User banned = activeUser();
        banned.ban();
        when(userReadPort.findByEmail("user@example.com")).thenReturn(Optional.of(banned));
        when(passwordEncoder.matches("raw-password", "$2a$hash")).thenReturn(true);

        assertThatThrownBy(() -> service.login(command)).isInstanceOf(UnauthorizedException.class);
        verify(tokenProvider, never()).issue(anyLong(), anyList());
    }

    @Test
    void login_fails_whenUserIsSoftDeleted() {
        User deleted = activeUser();
        deleted.softDelete();
        when(userReadPort.findByEmail("user@example.com")).thenReturn(Optional.of(deleted));
        when(passwordEncoder.matches("raw-password", "$2a$hash")).thenReturn(true);

        assertThatThrownBy(() -> service.login(command)).isInstanceOf(UnauthorizedException.class);
        verify(tokenProvider, never()).issue(anyLong(), anyList());
    }

    private RegisterUserCommand registerCommand(String role) {
        // La direccion del registro es opcional: estas pruebas cubren el alta sin ella.
        return new RegisterUserCommand(
                "new.user@example.com", "password123", "New User",
                role, "CC", "123456", "3001234567", true,
                null, null, null, null);
    }

    @Test
    void register_rejectsAdminRole() {
        // La restriccion "solo CLIENT/OFFERER" vive en este llamador (no en el mecanismo createUserAccount).
        assertThatThrownBy(() -> service.register(registerCommand("ADMIN")))
                .isInstanceOf(InvalidStateException.class);
        verify(userCreationServicePort, never()).createUserAccount(any());
    }

    @Test
    void register_delegatesToCreation_forPublicRole() {
        User created = User.builder().id(5L).roles(List.of(RoleName.CLIENT)).build();
        when(userCreationServicePort.createUserAccount(any())).thenReturn(created);
        when(tokenProvider.issue(anyLong(), anyList()))
                .thenReturn(new IssuedToken("jwt", LocalDateTime.now().plusHours(1)));

        AuthResult result = service.register(registerCommand("CLIENT"));

        assertThat(result.userId()).isEqualTo(5L);
        verify(userCreationServicePort).createUserAccount(any());
    }

    // =====================================================
    // RF-003 — solicitar recuperacion
    // =====================================================

    private static final RequestPasswordResetCommand RESET_REQUEST =
            new RequestPasswordResetCommand("user@example.com");

    private void stubTokenIssue() {
        when(passwordResetTokenServicePort.createToken(1L))
                .thenReturn(new IssuedResetToken("raw-token", LocalDateTime.now().plusMinutes(30)));
        when(resetLinkPort.buildResetLink("raw-token"))
                .thenReturn("http://localhost:5173/reset-password?token=raw-token");
    }

    @Test
    void requestPasswordReset_sendsTheLinkByEmailOnly() {
        when(userReadPort.findByEmail("user@example.com")).thenReturn(Optional.of(activeUser()));
        stubTokenIssue();

        service.requestPasswordReset(RESET_REQUEST);

        ArgumentCaptor<Set<ChannelName>> channels = ArgumentCaptor.forClass(Set.class);
        ArgumentCaptor<Map<String, String>> data = ArgumentCaptor.forClass(Map.class);
        verify(notificationServicePort).notify(
                eq(1L), eq("password_reset"), any(), any(), any(), any(),
                channels.capture(), data.capture());

        // Solo EMAIL: el usuario no puede iniciar sesion, una notificacion interna no le llegaria.
        assertThat(channels.getValue()).containsExactly(ChannelName.EMAIL);
        // El enlace viaja en protectedData, que NO se persiste: el token nunca toca la tabla notifications.
        assertThat(data.getValue().get("actionUrl")).contains("raw-token");
    }

    @Test
    void requestPasswordReset_staysSilentWhenTheEmailIsNotRegistered() {
        // Anti-enumeracion: no lanza ni notifica; el controlador respondera igual que en el caso feliz.
        when(userReadPort.findByEmail("user@example.com")).thenReturn(Optional.empty());

        service.requestPasswordReset(RESET_REQUEST);

        verify(passwordResetTokenServicePort, never()).createToken(any());
        verify(notificationServicePort, never()).notify(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void requestPasswordReset_ignoresBannedAndDeletedAccounts() {
        User banned = activeUser();
        banned.ban();
        when(userReadPort.findByEmail("user@example.com")).thenReturn(Optional.of(banned));
        service.requestPasswordReset(RESET_REQUEST);

        User deleted = activeUser();
        deleted.softDelete();
        when(userReadPort.findByEmail("user@example.com")).thenReturn(Optional.of(deleted));
        service.requestPasswordReset(RESET_REQUEST);

        verify(passwordResetTokenServicePort, never()).createToken(any());
        verify(notificationServicePort, never()).notify(any(), any(), any(), any(), any(), any(), any(), any());
    }

    // =====================================================
    // RF-003 — validar el enlace al abrirlo
    // =====================================================

    @Test
    void validatePasswordResetToken_passesWhenTheLinkStillWorks() {
        when(passwordResetTokenServicePort.validateToken("raw-token"))
                .thenReturn(TokenValidationResult.VALID);

        service.validatePasswordResetToken("raw-token");

        // Solo lectura: abrir el enlace no debe quemar el token (hay clientes de correo que lo previsualizan).
        verify(passwordResetTokenServicePort, never()).consumeToken(any());
    }

    @Test
    void validatePasswordResetToken_hidesWhyTheLinkFailed() {
        // Los tres motivos deben producir el MISMO mensaje: distinguirlos revelaria si el token existio.
        for (TokenValidationResult motivo : List.of(
                TokenValidationResult.NOT_FOUND, TokenValidationResult.EXPIRED, TokenValidationResult.USED)) {
            when(passwordResetTokenServicePort.validateToken("raw-token")).thenReturn(motivo);

            assertThatThrownBy(() -> service.validatePasswordResetToken("raw-token"))
                    .isInstanceOf(InvalidStateException.class)
                    .hasMessage(PasswordResetTokenServicePort.GENERIC_INVALID_TOKEN_MESSAGE);
        }
    }

    // =====================================================
    // RF-003 — confirmar nueva contrasena
    // =====================================================

    private static final ConfirmPasswordResetCommand RESET_CONFIRM =
            new ConfirmPasswordResetCommand("raw-token", "nueva-password");

    private PasswordResetToken consumedToken() {
        return PasswordResetToken.builder().id(9L).userId(1L).tokenHash("hash")
                .usedAt(LocalDateTime.now()).expiresAt(LocalDateTime.now().plusMinutes(5)).build();
    }

    @Test
    void confirmPasswordReset_setsTheNewPasswordHashedAndWarnsTheOwner() {
        when(passwordResetTokenServicePort.consumeToken("raw-token")).thenReturn(consumedToken());
        when(userReadPort.findById(1L)).thenReturn(Optional.of(activeUser()));
        when(passwordEncoder.encode("nueva-password")).thenReturn("$2a$nuevo-hash");

        service.confirmPasswordReset(RESET_CONFIRM);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userPersistencePort).update(saved.capture());
        // Se guarda el hash BCrypt, nunca la contrasena en claro.
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("$2a$nuevo-hash");
        // Aviso de contencion: si el cambio no lo hizo el titular, este correo es su senal de alarma.
        verify(notificationServicePort).notify(
                eq(1L), eq("password_changed"), any(), any(), any(), any(), any(), any());
    }

    @Test
    void confirmPasswordReset_takesTheUserFromTheTokenNotFromTheRequest() {
        // El userId sale del token encontrado en la tabla: es lo que impide cambiar la clave de otra cuenta.
        PasswordResetToken otherUsersToken = consumedToken();
        otherUsersToken.setUserId(42L);
        when(passwordResetTokenServicePort.consumeToken("raw-token")).thenReturn(otherUsersToken);
        when(userReadPort.findById(42L)).thenReturn(Optional.of(activeUser()));
        when(passwordEncoder.encode(any())).thenReturn("$2a$nuevo-hash");

        service.confirmPasswordReset(RESET_CONFIRM);

        verify(userReadPort).findById(42L);
    }

    @Test
    void confirmPasswordReset_propagatesTheGenericRejectionOfAnUnusableToken() {
        when(passwordResetTokenServicePort.consumeToken("raw-token"))
                .thenThrow(new InvalidStateException("Invalid or expired password reset token"));

        assertThatThrownBy(() -> service.confirmPasswordReset(RESET_CONFIRM))
                .isInstanceOf(InvalidStateException.class);
        verify(userPersistencePort, never()).update(any());
    }
}
