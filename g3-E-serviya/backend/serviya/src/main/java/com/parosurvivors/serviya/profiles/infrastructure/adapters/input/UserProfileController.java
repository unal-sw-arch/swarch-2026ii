package com.parosurvivors.serviya.profiles.infrastructure.adapters.input;

import com.parosurvivors.serviya.profiles.application.ports.input.UserProfileServicePort;
import com.parosurvivors.serviya.profiles.application.services.UserProfilePhotoStorageService;
import com.parosurvivors.serviya.profiles.infrastructure.adapters.input.api.UserProfileApi;
import com.parosurvivors.serviya.shared.ratelimit.RateLimit;
import com.parosurvivors.serviya.shared.security.CurrentUser;
import com.parosurvivors.serviya.profiles.infrastructure.dto.form.UpdateMainAddressForm;
import com.parosurvivors.serviya.profiles.infrastructure.dto.form.UpdateProfileForm;
import com.parosurvivors.serviya.profiles.infrastructure.dto.response.UserProfileResponse;
import com.parosurvivors.serviya.profiles.infrastructure.mappers.UserProfileWebMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Adaptador de entrada (REST) del perfil personal. Placeholder funcional; documentacion en {@link UserProfileApi}.
 * El requesterId se obtendra del JWT (TODO: integrar Spring Security).
 */
@RestController
@RequestMapping("/api/v1/users/me")
@RequiredArgsConstructor
public class UserProfileController implements UserProfileApi {

    private final UserProfileServicePort userProfileService;
    private final UserProfileWebMapper mapper;
    private final UserProfilePhotoStorageService userProfilePhotoStorageService;

    @Override
    @GetMapping("/profile")
    public ResponseEntity<UserProfileResponse> getProfileInfo() {
        return ResponseEntity.ok(mapper.toResponse(userProfileService.getProfileInfo(currentUserId())));
    }

    @Override
    @PatchMapping("/profile")
    public ResponseEntity<UserProfileResponse> patchProfile(@Valid @RequestBody UpdateProfileForm form) {
        return ResponseEntity.ok(mapper.toResponse(
                userProfileService.patchProfile(mapper.toCommand(form, currentUserId()))));
    }

    @PatchMapping("/profile/photo")
    @RateLimit("profile-photo")
    public ResponseEntity<UserProfileResponse> updateProfilePhoto(@RequestParam("photoUrl") MultipartFile photo) {
        String photoUrl = userProfilePhotoStorageService.storeProfilePhoto(currentUserId(), photo);
        return ResponseEntity.ok(mapper.toResponse(
                userProfileService.patchProfile(new com.parosurvivors.serviya.profiles.application.dto.command.UpdateProfileCommand(
                        currentUserId(), null, null, photoUrl, null))));
    }

    @Override
    @PatchMapping("/main-address")
    public ResponseEntity<Void> updateMainAddress(@Valid @RequestBody UpdateMainAddressForm form) {
        userProfileService.updateMainAddress(currentUserId(), form.addressId());
        return ResponseEntity.noContent().build();
    }

    /** Id del usuario autenticado, extraido del JWT por el contexto de seguridad. */
    private Long currentUserId() {
        return CurrentUser.id();
    }
}
