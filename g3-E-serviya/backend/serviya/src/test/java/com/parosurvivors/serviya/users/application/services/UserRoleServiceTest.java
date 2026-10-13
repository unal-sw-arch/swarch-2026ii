package com.parosurvivors.serviya.users.application.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.parosurvivors.serviya.shared.events.application.ports.output.DomainEventPublisherPort;
import com.parosurvivors.serviya.shared.events.domain.RoleAssignedEvent;
import com.parosurvivors.serviya.shared.exceptions.InvalidStateException;
import com.parosurvivors.serviya.users.application.ports.output.RolePersistencePort;
import com.parosurvivors.serviya.users.application.ports.output.UserRolePersistencePort;
import com.parosurvivors.serviya.users.domain.Role;
import com.parosurvivors.serviya.users.domain.RoleName;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** RF-010/011: auto-asignacion de rol. RF-065 reusa el mismo mecanismo (assignRole). */
@ExtendWith(MockitoExtension.class)
class UserRoleServiceTest {

    private static final Long USER_ID = 5L;
    private static final Integer OFFERER_ID = 2;

    @Mock UserRolePersistencePort userRolePersistencePort;
    @Mock RolePersistencePort rolePersistencePort;
    @Mock DomainEventPublisherPort domainEventPublisher;

    @InjectMocks UserRoleService service;

    private void offererRoleExists() {
        when(rolePersistencePort.findByName(RoleName.OFFERER))
                .thenReturn(Optional.of(Role.builder().id(OFFERER_ID).name(RoleName.OFFERER).build()));
    }

    /**
     * Al adquirir el rol se publica RoleAssignedEvent: es lo que hace que las metricas y el perfil de
     * oferente se inicialicen de forma desacoplada (sin que este servicio conozca esos modulos).
     */
    @Test
    void al_adquirir_rol_publica_el_evento_que_inicializa_las_filas_1a1() {
        offererRoleExists();
        when(userRolePersistencePort.existsByUserIdAndRoleId(USER_ID, OFFERER_ID)).thenReturn(false);

        service.acquireRole(USER_ID, "OFFERER");

        verify(userRolePersistencePort).assignRole(USER_ID, OFFERER_ID);
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(domainEventPublisher).publish(captor.capture());
        assertThat(captor.getValue()).isEqualTo(new RoleAssignedEvent(USER_ID, "OFFERER"));
    }

    /** Un rol no puede asignarse dos veces (constraint unico user+role). */
    @Test
    void no_permite_asignar_dos_veces_el_mismo_rol() {
        offererRoleExists();
        when(userRolePersistencePort.existsByUserIdAndRoleId(USER_ID, OFFERER_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.acquireRole(USER_ID, "OFFERER"))
                .isInstanceOf(InvalidStateException.class);

        verify(userRolePersistencePort, never()).assignRole(USER_ID, OFFERER_ID);
        verify(domainEventPublisher, never()).publish(org.mockito.ArgumentMatchers.any());
    }

    /** La unica via legitima de obtener ADMIN es que otro admin lo conceda (RF-065), nunca auto-asignarselo. */
    @Test
    void no_permite_auto_asignarse_el_rol_admin() {
        assertThatThrownBy(() -> service.acquireRole(USER_ID, "ADMIN"))
                .isInstanceOf(InvalidStateException.class)
                .hasMessageContaining("ADMIN");

        verify(domainEventPublisher, never()).publish(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rechaza_un_rol_inexistente() {
        assertThatThrownBy(() -> service.acquireRole(USER_ID, "SUPERUSER"))
                .isInstanceOf(InvalidStateException.class);
    }
}
