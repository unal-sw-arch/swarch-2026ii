package com.parosurvivors.serviya.users.application.services;

import com.parosurvivors.serviya.profiles.application.dto.command.CreateAddressCommand;
import com.parosurvivors.serviya.profiles.application.dto.command.CreateUserProfileCommand;
import com.parosurvivors.serviya.profiles.application.ports.input.AddressServicePort;
import com.parosurvivors.serviya.profiles.application.ports.input.OffererProfileServicePort;
import com.parosurvivors.serviya.profiles.application.ports.input.UserProfileServicePort;
import com.parosurvivors.serviya.profiles.domain.Address;
import com.parosurvivors.serviya.profiles.domain.ProfileType;
import com.parosurvivors.serviya.shared.exceptions.InvalidStateException;
import com.parosurvivors.serviya.users.application.dto.command.CreateUserAccountCommand;
import com.parosurvivors.serviya.users.application.ports.input.ConsentServicePort;
import com.parosurvivors.serviya.users.application.ports.input.UserCreationServicePort;
import com.parosurvivors.serviya.users.application.ports.input.UserRoleServicePort;
import com.parosurvivors.serviya.users.application.ports.input.UserServicePort;
import com.parosurvivors.serviya.users.domain.RoleName;
import com.parosurvivors.serviya.users.domain.User;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mecanismo interno compartido de creacion de usuario (RF-002), reutilizado por los llamadores expuestos
 * {@code register} (visitante) y {@code createUserByAdmin} (admin). Es atomico ({@link Transactional}):
 * credenciales + rol + perfil + consentimiento se crean en una sola transaccion.
 *
 * <p>NO aplica restricciones de POLITICA de rol: asigna el rol recibido —cualquiera, incluido ADMIN— vía el
 * mecanismo de bajo nivel {@code assignRole}. Que rol se permite lo decide cada llamador (register limita a
 * CLIENT/OFFERER; createUserByAdmin admite cualquiera). Aqui solo vive la regla RF-004 (consentimiento).
 */
@Component
@RequiredArgsConstructor
public class UserCreationService implements UserCreationServicePort {

    private final UserServicePort userServicePort;
    private final UserRoleServicePort userRoleServicePort;
    private final ConsentServicePort consentServicePort;
    private final UserProfileServicePort userProfileServicePort;
    private final OffererProfileServicePort offererProfileServicePort;
    /** Direccion principal opcional capturada en el registro (queda en "Mis direcciones"). */
    private final AddressServicePort addressServicePort;

    @Override
    @Transactional
    public User createUserAccount(CreateUserAccountCommand command) {
        // RF-004: el consentimiento debe ser explicito; si falta o es false, no se crea la cuenta.
        if (command.acceptedTerms() == null || !command.acceptedTerms()) {
            throw new InvalidStateException("Data usage consent is required to create an account");
        }

        // Rol recibido (cualquiera, sin restriccion de politica). La existencia del rol la valida assignRole.
        RoleName roleName = parseRole(command.role());

        // Credenciales (valida email unico y cifra la contrasena con bcrypt).
        User user = userServicePort.createUser(command.email(), command.password());

        // Asignacion via el mecanismo interno assignRole (por nombre), sin restricciones de politica.
        userRoleServicePort.assignRole(user.getId(), roleName);

        if (roleName == RoleName.OFFERER) {
            offererProfileServicePort.createOffererProfile(user.getId());
        }

        // Perfil personal (document/phone se cifran al persistir).
        userProfileServicePort.createProfile(new CreateUserProfileCommand(
                user.getId(),
                command.fullName(),
                nullToEmpty(command.documentType()),
                nullToEmpty(command.documentNumber()),
                nullToEmpty(command.phone()),
                ProfileType.NATURAL));

        // RF-004: registro del consentimiento aceptado.
        consentServicePort.createConsent(user.getId(), true);

        // Direccion principal (opcional): si el registro la incluyo, se crea aqui mismo y queda como
        // principal, de modo que el usuario entra con su direccion ya cargada en "Mis direcciones".
        // Va dentro de la misma transaccion: si falla, no queda ni cuenta ni direccion a medias.
        if (command.hasAddress()) {
            Address address = addressServicePort.createAddress(new CreateAddressCommand(
                    user.getId(),
                    command.addressLine(),
                    command.city(),
                    command.latitude(),
                    command.longitude()));
            userProfileServicePort.updateMainAddress(user.getId(), address.getId());
        }

        user.setRoles(List.of(roleName));
        return user;
    }

    private RoleName parseRole(String role) {
        try {
            return RoleName.valueOf(role == null ? "" : role.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new InvalidStateException("Invalid role: " + role);
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
