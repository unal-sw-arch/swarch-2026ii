package com.parosurvivors.serviya.profiles.infrastructure.adapters.input;

import com.parosurvivors.serviya.profiles.application.ports.input.OffererProfileServicePort;
import com.parosurvivors.serviya.profiles.infrastructure.adapters.input.api.OffererProfileApi;
import com.parosurvivors.serviya.profiles.infrastructure.dto.form.UpdateOffererProfileForm;
import com.parosurvivors.serviya.profiles.infrastructure.dto.response.OffererPublicProfileResponse;
import com.parosurvivors.serviya.profiles.infrastructure.mappers.OffererProfileWebMapper;
import com.parosurvivors.serviya.shared.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Adaptador de entrada (REST) del perfil de oferente. Placeholder funcional; documentacion en {@link OffererProfileApi}.
 */
@RestController
@RequestMapping("/api/v1/offerers")
@RequiredArgsConstructor
public class OffererProfileController implements OffererProfileApi {

    private final OffererProfileServicePort offererProfileService;
    private final OffererProfileWebMapper mapper;

    @Override
    @GetMapping("/me")
    public ResponseEntity<OffererPublicProfileResponse> getOwnPublicProfile() {
        return ResponseEntity.ok(mapper.toResponse(offererProfileService.getPublicProfile(currentUserId())));
    }

    @Override
    @GetMapping("/{id}")
    public ResponseEntity<OffererPublicProfileResponse> getPublicProfile(@PathVariable Long id) {
        return ResponseEntity.ok(mapper.toResponse(offererProfileService.getPublicProfile(id)));
    }

    @Override
    @PatchMapping("/me")
    public ResponseEntity<OffererPublicProfileResponse> patchOffererProfile(
            @Valid @RequestBody UpdateOffererProfileForm form) {
        return ResponseEntity.ok(mapper.toResponse(
                offererProfileService.patchOffererProfile(mapper.toCommand(form, currentUserId()))));
    }

    /** TODO: reemplazar por el id extraido del JWT autenticado. */
    private Long currentUserId() {
        return CurrentUser.id();
    }
}
