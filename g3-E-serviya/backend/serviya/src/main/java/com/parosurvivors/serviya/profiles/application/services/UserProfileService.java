package com.parosurvivors.serviya.profiles.application.services;

import com.parosurvivors.serviya.profiles.application.dto.command.CreateUserProfileCommand;
import com.parosurvivors.serviya.profiles.application.dto.command.UpdateProfileCommand;
import com.parosurvivors.serviya.profiles.application.ports.input.UserProfileServicePort;
import com.parosurvivors.serviya.profiles.application.ports.output.AddressPersistencePort;
import com.parosurvivors.serviya.profiles.application.ports.output.UserProfilePersistencePort;
import com.parosurvivors.serviya.profiles.domain.UserProfile;
import com.parosurvivors.serviya.shared.exceptions.ResourceNotFoundException;
import com.parosurvivors.serviya.shared.textfilter.application.ports.output.WordFilterPort;
import java.time.LocalDateTime;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Servicio del perfil personal. {@code createProfile} crea la fila de user_profiles durante el
 * registro (RF-002) y {@code getProfileInfo} expone la informacion del usuario autenticado
 * (RF-005). Ver documents/project-structure/estructura-servicios.docx.
 */
@Component
@RequiredArgsConstructor
public class UserProfileService implements UserProfileServicePort {

    private final UserProfilePersistencePort userProfilePersistencePort;
    private final AddressPersistencePort addressPersistencePort;
    /** Filtro de palabras (RNF-006) para los textos libres del perfil (nombre, descripcion). */
    private final WordFilterPort wordFilterPort;

    @Override
    public UserProfile createProfile(CreateUserProfileCommand command) {
        UserProfile profile = UserProfile.builder()
                .userId(command.userId())
                .fullName(command.fullName())
                .documentType(command.documentType())
                .documentNumber(command.documentNumber())
                .phoneNumber(command.phoneNumber())
                .profileType(command.profileType())
                .createdAt(LocalDateTime.now())
                .build();
        return userProfilePersistencePort.save(profile);
    }

    @Override
    public UserProfile getProfileInfo(Long userId) {
        return userProfilePersistencePort.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Profile not found for user: " + userId));
    }

    /**
     * RF-006: actualiza parcialmente el perfil del usuario autenticado.
     *
     * <p>Ownership: {@code command.userId()} lo inyecta el controller desde el JWT
     * ({@code CurrentUser.id()}), nunca del body, por lo que un usuario solo puede editar su propio
     * perfil. Documento (tipo/numero) no es editable: ni siquiera viaja en el command.
     * Los textos libres (nombre y descripcion) pasan por el filtro de palabras (RNF-006) antes de
     * persistir. El telefono se cifra al persistir (AES-256-GCM, PiiAttributeConverter).</p>
     */
    @Override
    @Transactional
    public UserProfile patchProfile(UpdateProfileCommand command) {
        UserProfile profile = userProfilePersistencePort.findByUserId(command.userId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Profile not found for user: " + command.userId()));

        profile.applyPartialUpdate(
                wordFilterPort.filter(command.fullName()),
                command.phone(),
                command.photoUrl(),
                wordFilterPort.filter(command.description()));

        return userProfilePersistencePort.save(profile);
    }

    @Override
    public void updateMainAddress(Long userId, Long addressId) {
        Optional<UserProfile> userProfile = userProfilePersistencePort.findByUserId(userId);

        if (userProfile.isPresent()) {
            UserProfile profile = userProfile.get();
            profile.setPrimaryAddressId(addressId);
            userProfilePersistencePort.save(profile);
        }
    }
}
