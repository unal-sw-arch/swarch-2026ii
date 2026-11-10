package com.parosurvivors.serviya.admin.application.services;

import com.parosurvivors.serviya.notifications.api.NotificationCommandApi;
import com.parosurvivors.serviya.requests.api.RequestCommandApi;
import com.parosurvivors.serviya.services.api.ServiceCommandApi;
import com.parosurvivors.serviya.shared.exceptions.InvalidStateException;
import com.parosurvivors.serviya.users.api.UserCommandApi;
import com.parosurvivors.serviya.users.api.UserQueryApi;
import com.parosurvivors.serviya.users.api.dto.UserSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock private UserQueryApi userQueryApi;
    @Mock private UserCommandApi userCommandApi;
    @Mock private RequestCommandApi requestCommandApi;
    @Mock private ServiceCommandApi serviceCommandApi;
    @Mock private NotificationCommandApi notificationCommandApi;
    @Mock private AdminActionGuard adminActionGuard;

    @InjectMocks
    private AdminService service;

    private static final Long ADMIN = 100L;
    private static final Long TARGET = 45L;

    private UserSnapshot user(Long id, boolean deleted) {
        return new UserSnapshot(id, "u" + id + "@example.com", false, deleted,
                deleted ? LocalDateTime.now() : null, LocalDateTime.now(), Set.of());
    }

    @Test
    void banUserAppliesGuardsBeforeBanning() {
        service.banUser(ADMIN, TARGET, "spam");

        InOrder inOrder = inOrder(adminActionGuard, userCommandApi);
        inOrder.verify(adminActionGuard).requireNotSelf(ADMIN, TARGET);
        inOrder.verify(adminActionGuard).requireAnotherActiveAdmin(TARGET);
        inOrder.verify(userCommandApi).banUser(TARGET, "spam");
    }

    @Test
    void banUserAbortsWhenGuardRejects() {
        doThrow(new InvalidStateException("self")).when(adminActionGuard).requireNotSelf(ADMIN, ADMIN);

        assertThatThrownBy(() -> service.banUser(ADMIN, ADMIN, "spam"))
                .isInstanceOf(InvalidStateException.class);
        verify(userCommandApi, never()).banUser(any(), any());
    }

    @Test
    void deleteUserAppliesGuardsBeforeDeleting() {
        service.deleteUser(ADMIN, TARGET);

        InOrder inOrder = inOrder(adminActionGuard, userCommandApi);
        inOrder.verify(adminActionGuard).requireNotSelf(ADMIN, TARGET);
        inOrder.verify(adminActionGuard).requireAnotherActiveAdmin(TARGET);
        inOrder.verify(userCommandApi).deleteUser(TARGET);
    }

    @Test
    void revokeAdminRoleAppliesGuards() {
        when(userQueryApi.findById(TARGET)).thenReturn(Optional.of(user(TARGET, false)));

        service.revokeRoleByAdmin(ADMIN, TARGET, "ADMIN");

        verify(adminActionGuard).requireNotSelf(ADMIN, TARGET);
        verify(adminActionGuard).requireAnotherActiveAdmin(TARGET);
        verify(userCommandApi).revokeRole(TARGET, "ADMIN");
    }

    @Test
    void revokeNonAdminRoleSkipsAdminGuards() {
        when(userQueryApi.findById(TARGET)).thenReturn(Optional.of(user(TARGET, false)));

        service.revokeRoleByAdmin(ADMIN, TARGET, "CLIENT");

        verify(adminActionGuard, never()).requireNotSelf(any(), any());
        verify(adminActionGuard, never()).requireAnotherActiveAdmin(any());
        verify(requestCommandApi).cancelActiveRequestsForRole(TARGET, false);
        verify(userCommandApi).revokeRole(TARGET, "CLIENT");
    }

    @Test
    void revokeOffererRoleAlsoDeactivatesServices() {
        when(userQueryApi.findById(TARGET)).thenReturn(Optional.of(user(TARGET, false)));

        service.revokeRoleByAdmin(ADMIN, TARGET, "OFFERER");

        verify(serviceCommandApi).deactivateAllByOfferer(TARGET);
        verify(requestCommandApi).cancelActiveRequestsForRole(TARGET, true);
        verify(userCommandApi).revokeRole(TARGET, "OFFERER");
    }
}
