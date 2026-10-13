package com.parosurvivors.serviya.profiles.application.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.parosurvivors.serviya.profiles.application.mappers.OffererAvailabilityCommandMapper;
import com.parosurvivors.serviya.profiles.application.ports.output.OffererAvailabilityPersistencePort;
import com.parosurvivors.serviya.profiles.domain.OffererAvailability;
import com.parosurvivors.serviya.shared.exceptions.ResourceNotFoundException;
import com.parosurvivors.serviya.shared.exceptions.UnauthorizedException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * IDOR (B4): borrar/activar/desactivar una franja exige ser su dueño o admin.
 * Antes del fix el servicio actuaba solo con el {@code slotId} del path.
 */
@ExtendWith(MockitoExtension.class)
class OffererAvailabilityServiceTest {

    @Mock OffererAvailabilityPersistencePort persistencePort;
    @Mock OffererAvailabilityCommandMapper commandMapper;

    @InjectMocks OffererAvailabilityService service;

    private static final Long SLOT_ID = 10L;
    private static final Long OWNER_ID = 7L;
    private static final Long INTRUDER_ID = 99L;

    private OffererAvailability slotOwnedBy(Long offererId) {
        return OffererAvailability.builder()
                .id(SLOT_ID)
                .offererId(offererId)
                .weekDay(1)
                .startTime(java.time.LocalTime.of(9, 0))
                .endTime(java.time.LocalTime.of(17, 0))
                .active(true)
                .build();
    }

    private void slotExistsFor(Long offererId) {
        when(persistencePort.findById(SLOT_ID)).thenReturn(Optional.of(slotOwnedBy(offererId)));
    }

    // ---------------------------------------------------------------- deleteSlot

    @Test
    void deleteSlot_deletesWhenRequesterIsOwner() {
        slotExistsFor(OWNER_ID);

        service.deleteSlot(SLOT_ID, OWNER_ID, false);

        verify(persistencePort).deleteById(SLOT_ID);
    }

    @Test
    void deleteSlot_deletesWhenRequesterIsAdmin() {
        slotExistsFor(OWNER_ID);

        service.deleteSlot(SLOT_ID, INTRUDER_ID, true);

        verify(persistencePort).deleteById(SLOT_ID);
    }

    @Test
    void deleteSlot_throwsUnauthorizedWhenRequesterIsNotOwner() {
        slotExistsFor(OWNER_ID);

        assertThatThrownBy(() -> service.deleteSlot(SLOT_ID, INTRUDER_ID, false))
                .isInstanceOf(UnauthorizedException.class);

        verify(persistencePort, never()).deleteById(anyLong());
    }

    @Test
    void deleteSlot_throwsUnauthorizedWhenRequesterMissing() {
        slotExistsFor(OWNER_ID);

        assertThatThrownBy(() -> service.deleteSlot(SLOT_ID, null, false))
                .isInstanceOf(UnauthorizedException.class);

        verify(persistencePort, never()).deleteById(anyLong());
    }

    @Test
    void deleteSlot_throwsNotFoundWhenSlotMissing() {
        when(persistencePort.findById(SLOT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteSlot(SLOT_ID, OWNER_ID, false))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(persistencePort, never()).deleteById(anyLong());
    }

    // ------------------------------------------------------------ activateSlot

    @Test
    void activateSlot_activatesWhenRequesterIsOwner() {
        slotExistsFor(OWNER_ID);

        service.activateSlot(SLOT_ID, OWNER_ID, false);

        verify(persistencePort).update(any(OffererAvailability.class));
    }

    @Test
    void activateSlot_allowsAdminOnSomeoneElsesSlot() {
        slotExistsFor(OWNER_ID);

        service.activateSlot(SLOT_ID, INTRUDER_ID, true);

        verify(persistencePort).update(any(OffererAvailability.class));
    }

    @Test
    void activateSlot_throwsUnauthorizedWhenRequesterIsNotOwner() {
        slotExistsFor(OWNER_ID);

        assertThatThrownBy(() -> service.activateSlot(SLOT_ID, INTRUDER_ID, false))
                .isInstanceOf(UnauthorizedException.class);

        verify(persistencePort, never()).update(any(OffererAvailability.class));
    }

    @Test
    void activateSlot_throwsNotFoundWhenSlotMissing() {
        when(persistencePort.findById(SLOT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.activateSlot(SLOT_ID, OWNER_ID, false))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---------------------------------------------------------- deactivateSlot

    @Test
    void deactivateSlot_deactivatesWhenRequesterIsOwner() {
        slotExistsFor(OWNER_ID);

        service.deactivateSlot(SLOT_ID, OWNER_ID, false);

        verify(persistencePort).update(any(OffererAvailability.class));
    }

    @Test
    void deactivateSlot_throwsUnauthorizedWhenRequesterIsNotOwner() {
        slotExistsFor(OWNER_ID);

        assertThatThrownBy(() -> service.deactivateSlot(SLOT_ID, INTRUDER_ID, false))
                .isInstanceOf(UnauthorizedException.class);

        verify(persistencePort, never()).update(any(OffererAvailability.class));
    }

    @Test
    void deactivateSlot_throwsNotFoundWhenSlotMissing() {
        when(persistencePort.findById(SLOT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deactivateSlot(SLOT_ID, OWNER_ID, false))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ------------------------------------------------------- estado resultante

    @Test
    void activateSlot_flipsActiveFlagOnOwnedSlot() {
        slotExistsFor(OWNER_ID);

        service.activateSlot(SLOT_ID, OWNER_ID, false);

        org.mockito.ArgumentCaptor<OffererAvailability> captor =
                org.mockito.ArgumentCaptor.forClass(OffererAvailability.class);
        verify(persistencePort).update(captor.capture());
        assertThat(captor.getValue().isActive()).isTrue();
    }

    @Test
    void deactivateSlot_flipsActiveFlagOnOwnedSlot() {
        slotExistsFor(OWNER_ID);

        service.deactivateSlot(SLOT_ID, OWNER_ID, false);

        org.mockito.ArgumentCaptor<OffererAvailability> captor =
                org.mockito.ArgumentCaptor.forClass(OffererAvailability.class);
        verify(persistencePort).update(captor.capture());
        assertThat(captor.getValue().isActive()).isFalse();
    }
}
