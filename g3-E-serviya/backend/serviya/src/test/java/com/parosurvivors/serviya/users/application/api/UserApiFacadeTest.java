package com.parosurvivors.serviya.users.application.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.parosurvivors.serviya.shared.contract.PageResult;
import com.parosurvivors.serviya.shared.exceptions.ResourceNotFoundException;
import com.parosurvivors.serviya.users.api.dto.CreateUserAccountCommand;
import com.parosurvivors.serviya.users.api.dto.UserSearchCriteria;
import com.parosurvivors.serviya.users.api.dto.UserSnapshot;
import com.parosurvivors.serviya.users.api.dto.UserSummary;
import com.parosurvivors.serviya.users.application.dto.item.UserSummaryItem;
import com.parosurvivors.serviya.users.application.ports.input.UserCreationServicePort;
import com.parosurvivors.serviya.users.application.ports.input.UserDeletionServicePort;
import com.parosurvivors.serviya.users.application.ports.input.UserQueryServicePort;
import com.parosurvivors.serviya.users.application.ports.input.UserRoleServicePort;
import com.parosurvivors.serviya.users.application.ports.input.UserServicePort;
import com.parosurvivors.serviya.users.application.ports.input.RoleServicePort;
import com.parosurvivors.serviya.users.domain.Role;
import com.parosurvivors.serviya.users.domain.RoleName;
import com.parosurvivors.serviya.users.domain.User;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class UserApiFacadeTest {

    @Mock
    private UserQueryServicePort userQueryServicePort;
    @Mock
    private UserServicePort userServicePort;
    @Mock
    private UserCreationServicePort userCreationServicePort;
    @Mock
    private UserRoleServicePort userRoleServicePort;
    @Mock
    private UserDeletionServicePort userDeletionServicePort;
    @Mock
    private RoleServicePort roleServicePort;

    @InjectMocks
    private UserApiFacade facade;

    @Test
    void findByIdMapsIdentityDataWithoutExposingPasswordHash() {
        User user = User.builder()
                .id(7L)
                .email("ana@example.com")
                .passwordHash("secret-hash")
                .banned(true)
                .createdAt(LocalDateTime.of(2026, 1, 2, 3, 4))
                .roles(List.of(RoleName.CLIENT, RoleName.OFFERER))
                .build();
        when(userQueryServicePort.getUserById(7L)).thenReturn(user);

        UserSnapshot snapshot = facade.findById(7L).orElseThrow();

        assertThat(snapshot.id()).isEqualTo(7L);
        assertThat(snapshot.email()).isEqualTo("ana@example.com");
        assertThat(snapshot.banned()).isTrue();
        assertThat(snapshot.deleted()).isFalse();
        assertThat(snapshot.roles()).containsExactlyInAnyOrder("CLIENT", "OFFERER");
    }

    @Test
    void findByIdReturnsEmptyWhenUserDoesNotExist() {
        when(userQueryServicePort.getUserById(99L))
                .thenThrow(new ResourceNotFoundException("not found"));

        assertThat(facade.findById(99L)).isEmpty();
    }

    @Test
    void searchMapsPageToContract() {        UserSummaryItem item = new UserSummaryItem(
                1L, "luis@example.com", "Luis", null, false, null,
                LocalDateTime.of(2026, 2, 3, 4, 5));
        when(userQueryServicePort.searchUsers(any(), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(item), PageRequest.of(0, 10), 1));

        PageResult<UserSummary> result = facade.search(
                new UserSearchCriteria(null, null, null, null, null), 0, 10);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).email()).isEqualTo("luis@example.com");
        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.page()).isZero();
    }

    @Test
    void assignRoleConvertsContractStringToInternalRole() {
        facade.assignRole(5L, "OFFERER");

        verify(userRoleServicePort).assignRole(5L, RoleName.OFFERER);
    }

    @Test
    void assignRoleRejectsUnknownRole() {
        assertThatThrownBy(() -> facade.assignRole(5L, "SUPERUSER"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SUPERUSER");
    }

    @Test
    void createUserAccountMapsAllContractFieldsToInternalCommand() {
        User created = User.builder().id(11L).email("new@example.com")
                .createdAt(LocalDateTime.now()).roles(List.of(RoleName.CLIENT)).build();
        when(userCreationServicePort.createUserAccount(any())).thenReturn(created);

        UserSnapshot snapshot = facade.createUserAccount(new CreateUserAccountCommand(
                "new@example.com", "raw-pass", "New User", "CLIENT",
                "CC", "123", "3001234567", true,
                "Calle 1", "Bogota", new BigDecimal("4.6"), new BigDecimal("-74.1")));

        ArgumentCaptor<com.parosurvivors.serviya.users.application.dto.command.CreateUserAccountCommand> captor =
                ArgumentCaptor.forClass(com.parosurvivors.serviya.users.application.dto.command.CreateUserAccountCommand.class);
        verify(userCreationServicePort).createUserAccount(captor.capture());
        assertThat(captor.getValue().email()).isEqualTo("new@example.com");
        assertThat(captor.getValue().role()).isEqualTo("CLIENT");
        assertThat(captor.getValue().latitude()).isEqualByComparingTo("4.6");
        assertThat(snapshot.id()).isEqualTo(11L);
    }

    @Test
    void getAllRolesMapsCatalog() {
        when(roleServicePort.getRoles()).thenReturn(List.of(
                Role.builder().id(1).name(RoleName.CLIENT).build(),
                Role.builder().id(3).name(RoleName.ADMIN).build()));

        assertThat(facade.getAllRoles())
                .extracting(role -> role.id() + ":" + role.name())
                .containsExactly("1:CLIENT", "3:ADMIN");
    }

}
