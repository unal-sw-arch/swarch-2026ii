package com.parosurvivors.serviya.profiles.infrastructure.adapters.input;

import com.parosurvivors.serviya.profiles.application.ports.input.OffererAvailabilityServicePort;
import com.parosurvivors.serviya.profiles.infrastructure.adapters.input.api.OffererAvailabilityApi;
import com.parosurvivors.serviya.profiles.infrastructure.dto.form.AvailabilitySlotForm;
import com.parosurvivors.serviya.profiles.infrastructure.dto.response.AvailabilitySlotResponse;
import com.parosurvivors.serviya.profiles.infrastructure.mappers.OffererAvailabilityWebMapper;
//import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.parosurvivors.serviya.shared.security.CurrentUser;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
/**
 * Adaptador de entrada (REST) de la disponibilidad general del oferente. Placeholder funcional;
 * documentacion en {@link OffererAvailabilityApi}.
*/
@Slf4j
@RestController
@RequestMapping("/api/v1/offerers/me/availability")
@RequiredArgsConstructor
public class OffererAvailabilityController implements OffererAvailabilityApi {

    private final OffererAvailabilityServicePort offererAvailabilityService;
    private final OffererAvailabilityWebMapper mapper;

    @Override
    @GetMapping
    public ResponseEntity<List<AvailabilitySlotResponse>> getSchedule() {
        return ResponseEntity.ok(mapper.toResponses(offererAvailabilityService.getSchedule(currentUserId())));
    }

    @Override
    @PutMapping
    public ResponseEntity<Void> setSchedule(@RequestBody List<AvailabilitySlotForm> slots) {
        offererAvailabilityService.setSchedule(currentUserId(), mapper.toCommands(slots));
        return ResponseEntity.noContent().build();
    }

    @Override
    @DeleteMapping("/slots/{id}")
    public ResponseEntity<Void> deleteSlot(@PathVariable Long id) {
        offererAvailabilityService.deleteSlot(id, currentUserId(), CurrentUser.isAdmin());
        return ResponseEntity.noContent().build();
    }

    @Override
    @PostMapping("/slots/{id}/activate")
    public ResponseEntity<Void> activateSlot(@PathVariable Long id) {
        offererAvailabilityService.activateSlot(id, currentUserId(), CurrentUser.isAdmin());
        return ResponseEntity.noContent().build();
    }

    @Override
    @PostMapping("/slots/{id}/deactivate")
    public ResponseEntity<Void> deactivateSlot(@PathVariable Long id) {
        offererAvailabilityService.deactivateSlot(id, currentUserId(), CurrentUser.isAdmin());
        return ResponseEntity.noContent().build();
    }

    private Long currentUserId() {
        return CurrentUser.id();
    }
}
