package com.parosurvivors.serviya.admin.application.services;

import com.parosurvivors.serviya.notifications.application.ports.input.NotificationServicePort;
import com.parosurvivors.serviya.requests.application.ports.input.ServiceRequestCommandServicePort;
import com.parosurvivors.serviya.services.application.ports.input.MarketplaceServicePort;
import com.parosurvivors.serviya.shared.exceptions.InvalidStateException;
import com.parosurvivors.serviya.users.application.ports.input.UserDeletionServicePort;
import com.parosurvivors.serviya.users.application.ports.input.UserQueryServicePort;
import com.parosurvivors.serviya.users.application.ports.input.UserRoleServicePort;
import com.parosurvivors.serviya.users.application.ports.input.UserServicePort;
import com.parosurvivors.serviya.users.domain.RoleName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock private UserServicePort userServicePort;
    @Mock private UserDeletionServicePort userDeletionServicePort;
    @Mock private UserRoleServicePort userRoleServicePort;
    @Mock private UserQueryServicePort userQueryServicePort;
    @Mock private MarketplaceServicePort marketplaceServicePort;
    @Mock private ServiceRequestCommandServicePort serviceRequestCommandServicePort;
    @Mock private NotificationServicePort notificationServicePort;
    @Mock private AdminActionGuard adminActionGuard;

    @InjectMocks
    private AdminService service;

    private static final Long ADMIN = 100L;
    private static final Long TARGET = 45L;

    @Test
    void banUserAppliesGuardsBeforeBanning() {
        service.banUser(ADMIN, TARGET, "spam");

        InOrder inOrder = inOrder(adminActionGuard, userServicePort);
        inOrder.verify(adminActionGuard).requireNotSelf(ADMIN, TARGET);
        inOrder.verify(adminActionGuard).requireAnotherActiveAdmin(TARGET);
        inOrder.verify(userServicePort).banUser(TARGET, "spam");
    }

    @Test
    void banUserAbortsWhenGuardRejects() {
        doThrow(new InvalidStateException("self")).when(adminActionGuard).requireNotSelf(ADMIN, ADMIN);

        assertThatThrownBy(() -> service.banUser(ADMIN, ADMIN, "spam"))
                .isInstanceOf(InvalidStateException.class);
        verify(userServicePort, never()).banUser(any(), any());
    }

    @Test
    void deleteUserAppliesGuardsBeforeDeleting() {
        service.deleteUser(ADMIN, TARGET);

        InOrder inOrder = inOrder(adminActionGuard, userDeletionServicePort);
        inOrder.verify(adminActionGuard).requireNotSelf(ADMIN, TARGET);
        inOrder.verify(adminActionGuard).requireAnotherActiveAdmin(TARGET);
        inOrder.verify(userDeletionServicePort).deleteUser(TARGET);
    }

    @Test
    void revokeAdminRoleAppliesGuards() {
        service.revokeRoleByAdmin(ADMIN, TARGET, "ADMIN");

        verify(adminActionGuard).requireNotSelf(ADMIN, TARGET);
        verify(adminActionGuard).requireAnotherActiveAdmin(TARGET);
        verify(userRoleServicePort).revokeRole(TARGET, RoleName.ADMIN);
    }

    @Test
    void revokeNonAdminRoleSkipsAdminGuards() {
        service.revokeRoleByAdmin(ADMIN, TARGET, "CLIENT");

        verify(adminActionGuard, never()).requireNotSelf(any(), any());
        verify(adminActionGuard, never()).requireAnotherActiveAdmin(any());
        verify(serviceRequestCommandServicePort).cancelActiveRequestsForRole(TARGET, false);
        verify(userRoleServicePort).revokeRole(TARGET, RoleName.CLIENT);
    }
}