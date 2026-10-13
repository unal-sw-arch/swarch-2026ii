package com.parosurvivors.serviya.users.application.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.parosurvivors.serviya.profiles.application.dto.command.CreateUserProfileCommand;
import com.parosurvivors.serviya.profiles.application.ports.input.OffererProfileServicePort;
import com.parosurvivors.serviya.profiles.application.ports.input.UserProfileServicePort;
import com.parosurvivors.serviya.shared.exceptions.InvalidStateException;
import com.parosurvivors.serviya.users.application.dto.command.CreateUserAccountCommand;
import com.parosurvivors.serviya.users.application.ports.input.ConsentServicePort;
import com.parosurvivors.serviya.users.application.ports.input.UserRoleServicePort;
import com.parosurvivors.serviya.users.application.ports.input.UserServicePort;
import com.parosurvivors.serviya.users.domain.RoleName;
import com.parosurvivors.serviya.users.domain.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Reglas del mecanismo interno de creacion (HU-002/HU-004). NO restringe la POLITICA de rol: asigna el rol
 * recibido —incluido ADMIN— delegando en el mecanismo assignRole(userId, RoleName), que es el unico punto
 * que valida la existencia del rol. La restriccion "solo CLIENT/OFFERER" vive en el llamador register
 * (ver UserAuthenticationServiceTest). Aqui solo aplica RF-004 (consentimiento).
 */
@ExtendWith(MockitoExtension.class)
class UserCreationServiceTest {

    @Mock UserServicePort userServicePort;
    @Mock UserRoleServicePort userRoleServicePort;
    @Mock ConsentServicePort consentServicePort;
    @Mock UserProfileServicePort userProfileServicePort;
    @Mock com.parosurvivors.serviya.profiles.application.ports.input.AddressServicePort addressServicePort;
    @Mock OffererProfileServicePort offererProfileServicePort;

    @InjectMocks UserCreationService service;

    private CreateUserAccountCommand command(String role, Boolean accepted) {
        return new CreateUserAccountCommand(
                "new.user@example.com", "password123", "New User",
                role, "CC", "123456", "3001234567", accepted,
                null, null, null, null);
    }

    @Test
    void createsAccountAtomically_assigningRoleProfileAndConsent() {
        when(userServicePort.createUser(anyString(), anyString()))
                .thenReturn(User.builder().id(42L).email("new.user@example.com").build());

        User result = service.createUserAccount(command("CLIENT", true));

        assertThat(result.getId()).isEqualTo(42L);
        assertThat(result.getRoles()).containsExactly(RoleName.CLIENT);
        verify(userServicePort).createUser("new.user@example.com", "password123");
        // Delega en assignRole(userId, RoleName) — la existencia del rol la valida ese mecanismo, no aqui.
        verify(userRoleServicePort).assignRole(42L, RoleName.CLIENT);
        verify(userProfileServicePort).createProfile(any(CreateUserProfileCommand.class));
        verify(consentServicePort).createConsent(42L, true);
    }

    @Test
    void rejectsRegistration_whenConsentNotAccepted() {
        assertThatThrownBy(() -> service.createUserAccount(command("CLIENT", false)))
                .isInstanceOf(InvalidStateException.class);

        // HU-004: nada se persiste si no hay consentimiento.
        verify(userServicePort, never()).createUser(anyString(), anyString());
        verify(consentServicePort, never()).createConsent(anyLong(), anyBoolean());
    }

    @Test
    void rejectsRegistration_whenConsentIsNull() {
        assertThatThrownBy(() -> service.createUserAccount(command("CLIENT", null)))
                .isInstanceOf(InvalidStateException.class);
        verify(userServicePort, never()).createUser(anyString(), anyString());
    }

    @Test
    void allowsAdminRole_sinceRolePolicyLivesInCallers() {
        // El mecanismo interno NO restringe roles: crear un ADMIN es valido aqui (lo llama createUserByAdmin).
        when(userServicePort.createUser(anyString(), anyString()))
                .thenReturn(User.builder().id(9L).build());

        User result = service.createUserAccount(command("ADMIN", true));

        assertThat(result.getRoles()).containsExactly(RoleName.ADMIN);
        verify(userRoleServicePort).assignRole(9L, RoleName.ADMIN);
    }

    @Test
    void rejectsRegistration_whenRoleIsInvalid() {
        assertThatThrownBy(() -> service.createUserAccount(command("SUPERUSER", true)))
                .isInstanceOf(InvalidStateException.class);
        verify(userServicePort, never()).createUser(anyString(), anyString());
    }

    @Test
    void allowsOffererRegistration() {
        when(userServicePort.createUser(anyString(), anyString()))
                .thenReturn(User.builder().id(7L).build());

        User result = service.createUserAccount(command("OFFERER", true));

        assertThat(result.getRoles()).containsExactly(RoleName.OFFERER);
        verify(userRoleServicePort).assignRole(7L, RoleName.OFFERER);
        verify(offererProfileServicePort).createOffererProfile(7L);
    }

    private CreateUserAccountCommand commandWithAddress() {
        return new CreateUserAccountCommand(
                "new.user@example.com", "password123", "New User",
                "CLIENT", "CC", "123456", "3001234567", true,
                "Calle 45 #12-34", "Bogota",
                new java.math.BigDecimal("4.62"), new java.math.BigDecimal("-74.06"));
    }

    /** Si el registro trae direccion, se crea y queda como principal, en la misma transaccion. */
    @Test
    void crea_la_direccion_del_registro_y_la_deja_como_principal() {
        when(userServicePort.createUser(anyString(), anyString()))
                .thenReturn(User.builder().id(7L).email("new.user@example.com").build());
        when(addressServicePort.createAddress(any()))
                .thenReturn(com.parosurvivors.serviya.profiles.domain.Address.builder().id(99L).userId(7L).build());

        service.createUserAccount(commandWithAddress());

        verify(addressServicePort).createAddress(any());
        verify(userProfileServicePort).updateMainAddress(7L, 99L);
    }

    /** La direccion es OPCIONAL: sin ella el alta funciona igual y no se toca el modulo de direcciones. */
    @Test
    void sin_direccion_no_crea_ninguna() {
        when(userServicePort.createUser(anyString(), anyString()))
                .thenReturn(User.builder().id(7L).email("new.user@example.com").build());

        service.createUserAccount(command("CLIENT", true));

        verify(addressServicePort, never()).createAddress(any());
        verify(userProfileServicePort, never()).updateMainAddress(anyLong(), anyLong());
    }
}
