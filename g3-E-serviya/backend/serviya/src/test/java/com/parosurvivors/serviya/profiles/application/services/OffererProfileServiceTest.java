package com.parosurvivors.serviya.profiles.application.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.parosurvivors.serviya.metrics.application.ports.input.OffererMetricsServicePort;
import com.parosurvivors.serviya.profiles.application.dto.command.UpdateOffererProfileCommand;
import com.parosurvivors.serviya.profiles.application.ports.output.OffererProfilePersistencePort;
import com.parosurvivors.serviya.profiles.application.ports.output.UserProfilePersistencePort;
import com.parosurvivors.serviya.profiles.domain.OffererProfile;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OffererProfileServiceTest {

    @Mock OffererProfilePersistencePort offererProfilePersistencePort;
    @Mock UserProfilePersistencePort userProfilePersistencePort;
    @Mock OffererMetricsServicePort offererMetricsService;

    @InjectMocks OffererProfileService service;

    @Test
    void patchesExistingOffererProfile() {
        OffererProfile existing = OffererProfile.builder()
                .userId(7L)
                .whatsappNumber("111")
                .publicDescription("old")
                .specialty("old")
                .build();

        when(offererProfilePersistencePort.findByUserId(7L)).thenReturn(Optional.of(existing));
        when(offererProfilePersistencePort.save(any(OffererProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OffererProfile result = service.patchOffererProfile(new UpdateOffererProfileCommand(
                7L,
                "222",
                "new description",
                "new specialty"));

        assertThat(result.getWhatsappNumber()).isEqualTo("222");
        assertThat(result.getPublicDescription()).isEqualTo("new description");
        assertThat(result.getSpecialty()).isEqualTo("new specialty");
        verify(offererProfilePersistencePort).save(any(OffererProfile.class));
    }
}
