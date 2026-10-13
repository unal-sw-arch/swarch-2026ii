package com.parosurvivors.serviya.users.application.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.parosurvivors.serviya.shared.exceptions.ResourceNotFoundException;
import com.parosurvivors.serviya.users.application.dto.command.ChangePasswordCommand;
import com.parosurvivors.serviya.users.application.ports.output.UserPersistencePort;
import com.parosurvivors.serviya.users.application.ports.output.UserReadPort;
import com.parosurvivors.serviya.users.domain.User;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * RF-007: cambio de contrasena propia. Valida que el servicio verifique la contrasena actual,
 * codifique la nueva y persista el cambio.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock UserReadPort userReadPort;
    @Mock UserPersistencePort userPersistencePort;
    @Mock PasswordEncoder passwordEncoder;

    @InjectMocks UserService service;

    private static final Long USER_ID = 1L;
    private static final String CURRENT_PASSWORD = "oldPassword123";
    private static final String NEW_PASSWORD = "newPassword456";
    private static final String CURRENT_HASH = "$2a$oldHash";
    private static final String NEW_HASH = "$2a$newHash";

    private User existingUser() {
        return User.builder()
                .id(USER_ID)
                .email("user@example.com")
                .passwordHash(CURRENT_HASH)
                .banned(false)
                .build();
    }

    private ChangePasswordCommand command() {
        return new ChangePasswordCommand(USER_ID, CURRENT_PASSWORD, NEW_PASSWORD);
    }

    @Test
    void changePassword_succeeds_withValidCredentials() {
        when(userReadPort.findById(USER_ID)).thenReturn(Optional.of(existingUser()));
        when(passwordEncoder.matches(CURRENT_PASSWORD, CURRENT_HASH)).thenReturn(true);
        when(passwordEncoder.encode(NEW_PASSWORD)).thenReturn(NEW_HASH);

        service.changePassword(command());

        verify(userPersistencePort).update(any(User.class));
        assertThat(existingUser().getPasswordHash()).isEqualTo(CURRENT_HASH);
    }

    @Test
    void changePassword_persistsNewHash() {
        User user = existingUser();
        when(userReadPort.findById(USER_ID)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(CURRENT_PASSWORD, CURRENT_HASH)).thenReturn(true);
        when(passwordEncoder.encode(NEW_PASSWORD)).thenReturn(NEW_HASH);

        service.changePassword(command());

        assertThat(user.getPasswordHash()).isEqualTo(NEW_HASH);
        verify(userPersistencePort).update(user);
    }

    @Test
    void changePassword_fails_whenUserNotFound() {
        when(userReadPort.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.changePassword(command()))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(userPersistencePort, never()).update(any());
    }

    @Test
    void changePassword_fails_whenCurrentPasswordIncorrect() {
        when(userReadPort.findById(USER_ID)).thenReturn(Optional.of(existingUser()));
        when(passwordEncoder.matches(CURRENT_PASSWORD, CURRENT_HASH)).thenReturn(false);

        assertThatThrownBy(() -> service.changePassword(command()))
                .isInstanceOf(IllegalArgumentException.class);

        verify(userPersistencePort, never()).update(any());
    }

    @Test
    void changePassword_doesNotEncode_whenCurrentPasswordFails() {
        when(userReadPort.findById(USER_ID)).thenReturn(Optional.of(existingUser()));
        when(passwordEncoder.matches(CURRENT_PASSWORD, CURRENT_HASH)).thenReturn(false);

        assertThatThrownBy(() -> service.changePassword(command()))
                .isInstanceOf(IllegalArgumentException.class);

        verify(passwordEncoder, never()).encode(NEW_PASSWORD);
        verify(userPersistencePort, never()).update(any());
    }
}
