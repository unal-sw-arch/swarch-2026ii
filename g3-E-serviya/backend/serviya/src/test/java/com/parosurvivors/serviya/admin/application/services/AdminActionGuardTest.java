package com.parosurvivors.serviya.admin.application.services;

import com.parosurvivors.serviya.shared.exceptions.InvalidStateException;
import com.parosurvivors.serviya.users.api.UserQueryApi;
import com.parosurvivors.serviya.users.api.dto.UserSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminActionGuardTest {

    @Mock private UserQueryApi userQueryApi;

    @InjectMocks
    private AdminActionGuard guard;

    private static final Long ADMIN = 1L;
    private static final Long TARGET = 2L;

    private UserSnapshot user(Long id, boolean banned, boolean deleted) {
        return new UserSnapshot(id, "u" + id + "@example.com", banned, deleted,
                deleted ? LocalDateTime.now() : null, LocalDateTime.now(), java.util.Set.of());
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
        when(userQueryApi.hasRole(TARGET, "ADMIN")).thenReturn(false);

        assertThatCode(() -> guard.requireAnotherActiveAdmin(TARGET))
                .doesNotThrowAnyException();
        verify(userQueryApi, never()).findUserIdsByRole("ADMIN");
    }

    @Test
    void lastAdminGuardAllowsWhenAnotherActiveAdminExists() {
        when(userQueryApi.hasRole(TARGET, "ADMIN")).thenReturn(true);
        when(userQueryApi.findUserIdsByRole("ADMIN")).thenReturn(List.of(TARGET, ADMIN));
        when(userQueryApi.findById(ADMIN)).thenReturn(Optional.of(user(ADMIN, false, false)));

        assertThatCode(() -> guard.requireAnotherActiveAdmin(TARGET))
                .doesNotThrowAnyException();
    }

    @Test
    void lastAdminGuardRejectsWhenTargetIsTheOnlyAdmin() {
        when(userQueryApi.hasRole(TARGET, "ADMIN")).thenReturn(true);
        when(userQueryApi.findUserIdsByRole("ADMIN")).thenReturn(List.of(TARGET));

        assertThatThrownBy(() -> guard.requireAnotherActiveAdmin(TARGET))
                .isInstanceOf(InvalidStateException.class);
    }

    @Test
    void lastAdminGuardRejectsWhenRemainingAdminsAreNotActive() {
        when(userQueryApi.hasRole(TARGET, "ADMIN")).thenReturn(true);
        when(userQueryApi.findUserIdsByRole("ADMIN")).thenReturn(List.of(TARGET, ADMIN));
        when(userQueryApi.findById(ADMIN)).thenReturn(Optional.of(user(ADMIN, true, false)));

        assertThatThrownBy(() -> guard.requireAnotherActiveAdmin(TARGET))
                .isInstanceOf(InvalidStateException.class);
    }
}
