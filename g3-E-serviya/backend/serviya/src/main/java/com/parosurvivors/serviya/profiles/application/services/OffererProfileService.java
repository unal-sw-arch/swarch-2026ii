package com.parosurvivors.serviya.profiles.application.services;

import com.parosurvivors.serviya.profiles.application.dto.command.UpdateOffererProfileCommand;
import com.parosurvivors.serviya.profiles.application.ports.input.OffererProfileServicePort;
import com.parosurvivors.serviya.profiles.application.ports.output.OffererProfilePersistencePort;
import com.parosurvivors.serviya.profiles.domain.OffererProfile;
import com.parosurvivors.serviya.shared.exceptions.ResourceNotFoundException;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OffererProfileService implements OffererProfileServicePort {

    private final OffererProfilePersistencePort offererProfilePersistencePort;

    @Override
    public OffererProfile createOffererProfile(Long userId) {
        OffererProfile profile = OffererProfile.builder()
                .userId(userId)
                .whatsappNumber("")
                .publicDescription("")
                .specialty("")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        return offererProfilePersistencePort.save(profile);
    }

    @Override
    public OffererProfile getPublicProfile(Long userId) {
        return offererProfilePersistencePort.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Offerer profile not found for userId: " + userId));
    }

    @Override
    public OffererProfile patchOffererProfile(UpdateOffererProfileCommand command) {
        OffererProfile existing = offererProfilePersistencePort.findByUserId(command.userId())
                .orElseGet(() -> createOffererProfile(command.userId()));

        if (command.whatsappNumber() != null) {
            existing.setWhatsappNumber(command.whatsappNumber());
        }
        if (command.publicDescription() != null) {
            existing.setPublicDescription(command.publicDescription());
        }
        if (command.specialty() != null) {
            existing.setSpecialty(command.specialty());
        }
        existing.setUpdatedAt(LocalDateTime.now());

        return offererProfilePersistencePort.save(existing);
    }
}
