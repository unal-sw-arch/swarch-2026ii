package com.parosurvivors.serviya.admin.application.services;

import com.parosurvivors.serviya.shared.exceptions.InvalidStateException;
import com.parosurvivors.serviya.users.application.ports.input.UserQueryServicePort;
import com.parosurvivors.serviya.users.application.ports.input.UserRoleServicePort;
import com.parosurvivors.serviya.users.domain.RoleName;
import com.parosurvivors.serviya.users.domain.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminActionGuardTest {

    @Mock private UserRoleServicePort userRoleServicePort;
    @Mock private UserQueryServicePort userQueryServicePort;

    @InjectMocks
    private AdminActionGuard guard;

    private static final Long ADMIN = 1L;
    private static final Long TARGET = 2L;

    private User user(Long id, boolean banned, boolean deleted) {
        return User.builder()
                .id(id)
                .banned(banned)
                .deletedAt(deleted ? LocalDateTime.now() : null)
                .build();
    }

    @Test
    void requireNotSelfRejectsActingOnOwnAccount() {
        assertThatThrownBy(() -> guard.requireNotSelf(ADMIN, ADMIN))
                .isInstanceOf(InvalidStateException.class);
    }

    @Test
    void requireNotSelfAllowsActingOnAnotherAccount() {
        assertThatCode(() -> guard.requireNotSelf(ADMIN, TARGET))
                .doesNotThrowAnyException();
    }

    @Test
    void requireNotSelfAllowsNullAdminId() {
        assertThatCode(() -> guard.requireNotSelf(null, TARGET))
                .doesNotThrowAnyException();
    }

    @Test
    void lastAdminGuardSkippedWhenTargetIsNotAdmin() {
        when(userRoleServicePort.hasRole(TARGET, RoleName.ADMIN.name())).thenReturn(false);

        assertThatCode(() -> guard.requireAnotherActiveAdmin(TARGET))
                .doesNotThrowAnyException();
        verify(userRoleServicePort, never()).findUserIdsByRole(RoleName.ADMIN);
    }

    @Test
    void lastAdminGuardAllowsWhenAnotherActiveAdminExists() {
        when(userRoleServicePort.hasRole(TARGET, RoleName.ADMIN.name())).thenReturn(true);
        when(userRoleServicePort.findUserIdsByRole(RoleName.ADMIN)).thenReturn(List.of(TARGET, ADMIN));
        when(userQueryServicePort.getUserById(ADMIN)).thenReturn(user(ADMIN, false, false));

        assertThatCode(() -> guard.requireAnotherActiveAdmin(TARGET))
                .doesNotThrowAnyException();
    }

    @Test
    void lastAdminGuardRejectsWhenTargetIsTheOnlyAdmin() {
        when(userRoleServicePort.hasRole(TARGET, RoleName.ADMIN.name())).thenReturn(true);
        when(userRoleServicePort.findUserIdsByRole(RoleName.ADMIN)).thenReturn(List.of(TARGET));

        assertThatThrownBy(() -> guard.requireAnotherActiveAdmin(TARGET))
                .isInstanceOf(InvalidStateException.class);
    }

    @Test
    void lastAdminGuardRejectsWhenRemainingAdminsAreNotActive() {
        when(userRoleServicePort.hasRole(TARGET, RoleName.ADMIN.name())).thenReturn(true);
        when(userRoleServicePort.findUserIdsByRole(RoleName.ADMIN)).thenReturn(List.of(TARGET, ADMIN));
        when(userQueryServicePort.getUserById(ADMIN)).thenReturn(user(ADMIN, true, false));

        assertThatThrownBy(() -> guard.requireAnotherActiveAdmin(TARGET))
                .isInstanceOf(InvalidStateException.class);
    }
}