package com.parosurvivors.serviya.profiles.application.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.parosurvivors.serviya.profiles.api.dto.AddressSnapshot;
import com.parosurvivors.serviya.profiles.api.dto.CreateAddressCommand;
import com.parosurvivors.serviya.profiles.api.dto.CreateProfileCommand;
import com.parosurvivors.serviya.profiles.api.dto.OffererAvailabilitySnapshot;
import com.parosurvivors.serviya.profiles.api.dto.OffererProfileSummarySnapshot;
import com.parosurvivors.serviya.profiles.api.dto.ProfileSnapshot;
import com.parosurvivors.serviya.profiles.application.ports.input.AddressServicePort;
import com.parosurvivors.serviya.profiles.application.ports.input.OffererProfileServicePort;
import com.parosurvivors.serviya.profiles.application.ports.input.UserProfileServicePort;
import com.parosurvivors.serviya.profiles.application.ports.output.AddressPersistencePort;
import com.parosurvivors.serviya.profiles.application.ports.output.OffererAvailabilityPersistencePort;
import com.parosurvivors.serviya.profiles.application.ports.output.OffererProfilePersistencePort;
import com.parosurvivors.serviya.profiles.application.ports.output.UserProfilePersistencePort;
import com.parosurvivors.serviya.profiles.domain.Address;
import com.parosurvivors.serviya.profiles.domain.OffererAvailability;
import com.parosurvivors.serviya.profiles.domain.OffererProfile;
import com.parosurvivors.serviya.profiles.domain.ProfileType;
import com.parosurvivors.serviya.profiles.domain.UserProfile;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProfileApiFacadeTest {

    @Mock
    private UserProfilePersistencePort userProfilePersistencePort;
    @Mock
    private OffererProfilePersistencePort offererProfilePersistencePort;
    @Mock
    private AddressPersistencePort addressPersistencePort;
    @Mock
    private OffererAvailabilityPersistencePort offererAvailabilityPersistencePort;
    @Mock
    private UserProfileServicePort userProfileServicePort;
    @Mock
    private OffererProfileServicePort offererProfileServicePort;
    @Mock
    private AddressServicePort addressServicePort;

    @InjectMocks
    private ProfileApiFacade facade;

    @Test
    void findProfileMapsAllFields() {
        UserProfile profile = UserProfile.builder()
                .id(10L)
                .userId(7L)
                .fullName("Ana")
                .documentType("CC")
                .documentNumber("123")
                .phoneNumber("3001234567")
                .primaryAddressId(5L)
                .profilePhotoUrl("photo.png")
                .bio("bio")
                .profileType(ProfileType.COMPANY)
                .createdAt(LocalDateTime.of(2026, 1, 2, 3, 4))
                .build();
        when(userProfilePersistencePort.findByUserId(7L)).thenReturn(Optional.of(profile));

        ProfileSnapshot snapshot = facade.findProfile(7L).orElseThrow();

        assertThat(snapshot.id()).isEqualTo(10L);
        assertThat(snapshot.userId()).isEqualTo(7L);
        assertThat(snapshot.fullName()).isEqualTo("Ana");
        assertThat(snapshot.primaryAddressId()).isEqualTo(5L);
        assertThat(snapshot.profileType()).isEqualTo("COMPANY");
    }

    @Test
    void findProfileReturnsEmptyWhenMissing() {
        when(userProfilePersistencePort.findByUserId(99L)).thenReturn(Optional.empty());

        assertThat(facade.findProfile(99L)).isEmpty();
    }

    @Test
    void findProfilesMapsBatch() {
        UserProfile first = UserProfile.builder().id(1L).userId(10L).fullName("A").build();
        UserProfile second = UserProfile.builder().id(2L).userId(20L).fullName("B").build();
        when(userProfilePersistencePort.findByUserIdIn(Set.of(10L, 20L)))
                .thenReturn(List.of(first, second));

        List<ProfileSnapshot> result = facade.findProfiles(Set.of(10L, 20L));

        assertThat(result).extracting(ProfileSnapshot::userId).containsExactly(10L, 20L);
    }

    @Test
    void getOffererSummaryCombinesBothProfilesWithoutMetrics() {
        UserProfile userProfile = UserProfile.builder()
                .userId(7L).fullName("Ana").profilePhotoUrl("photo.png").build();
        OffererProfile offererProfile = OffererProfile.builder()
                .userId(7L).specialty("Plumbing").build();
        when(userProfilePersistencePort.findByUserId(7L)).thenReturn(Optional.of(userProfile));
        when(offererProfilePersistencePort.findByUserId(7L)).thenReturn(Optional.of(offererProfile));

        OffererProfileSummarySnapshot summary = facade.getOffererSummary(7L).orElseThrow();

        assertThat(summary.userId()).isEqualTo(7L);
        assertThat(summary.fullName()).isEqualTo("Ana");
        assertThat(summary.profilePhotoUrl()).isEqualTo("photo.png");
        assertThat(summary.specialty()).isEqualTo("Plumbing");
    }

    @Test
    void getOffererSummaryReturnsEmptyWhenOffererProfileIsMissing() {
        when(userProfilePersistencePort.findByUserId(7L))
                .thenReturn(Optional.of(UserProfile.builder().userId(7L).build()));
        when(offererProfilePersistencePort.findByUserId(7L)).thenReturn(Optional.empty());

        assertThat(facade.getOffererSummary(7L)).isEmpty();
    }

    @Test
    void findAddressMapsAndMissingReturnsEmpty() {
        Address address = Address.builder()
                .id(3L).userId(7L).addressLine("Calle 1").city("Bogota")
                .latitude(new BigDecimal("4.6")).longitude(new BigDecimal("-74.1"))
                .createdAt(LocalDateTime.of(2026, 2, 3, 4, 5))
                .build();
        when(addressPersistencePort.findById(3L)).thenReturn(Optional.of(address));

        AddressSnapshot snapshot = facade.findAddress(3L).orElseThrow();
        assertThat(snapshot.city()).isEqualTo("Bogota");
        assertThat(snapshot.latitude()).isEqualByComparingTo("4.6");

        when(addressPersistencePort.findById(99L)).thenReturn(Optional.empty());
        assertThat(facade.findAddress(99L)).isEmpty();
    }

    @Test
    void getAvailabilitiesPreservesProfilesWeekDayConvention() {
        OffererAvailability availability = OffererAvailability.builder()
                .id(1L).offererId(7L).weekDay(0)
                .startTime(LocalTime.of(8, 0)).endTime(LocalTime.of(17, 0)).active(true)
                .build();
        when(offererAvailabilityPersistencePort.findByOffererId(7L))
                .thenReturn(List.of(availability));

        List<OffererAvailabilitySnapshot> result = facade.getAvailabilities(7L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).weekDay()).isZero();
        assertThat(result.get(0).active()).isTrue();
    }

    @Test
    void createProfileConvertsProfileTypeString() {
        UserProfile created = UserProfile.builder()
                .id(10L).userId(7L).fullName("Ana").profileType(ProfileType.COMPANY).build();
        when(userProfileServicePort.createProfile(any())).thenReturn(created);

        ProfileSnapshot snapshot = facade.createProfile(new CreateProfileCommand(
                7L, "Ana", "NIT", "900", "3001234567", "COMPANY"));

        ArgumentCaptor<com.parosurvivors.serviya.profiles.application.dto.command.CreateUserProfileCommand> captor =
                ArgumentCaptor.forClass(
                        com.parosurvivors.serviya.profiles.application.dto.command.CreateUserProfileCommand.class);
        verify(userProfileServicePort).createProfile(captor.capture());
        assertThat(captor.getValue().userId()).isEqualTo(7L);
        assertThat(captor.getValue().profileType()).isEqualTo(ProfileType.COMPANY);
        assertThat(snapshot.profileType()).isEqualTo("COMPANY");
    }

    @Test
    void createProfileRejectsUnknownProfileType() {
        assertThatThrownBy(() -> facade.createProfile(new CreateProfileCommand(
                7L, "Ana", "CC", "123", "300", "UNKNOWN")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("UNKNOWN");
    }

    @Test
    void createAddressMapsCommand() {
        Address created = Address.builder().id(3L).userId(7L).addressLine("Calle 1").city("Bogota").build();
        when(addressServicePort.createAddress(any())).thenReturn(created);

        AddressSnapshot snapshot = facade.createAddress(new CreateAddressCommand(
                7L, "Calle 1", "Bogota", new BigDecimal("4.6"), new BigDecimal("-74.1")));

        ArgumentCaptor<com.parosurvivors.serviya.profiles.application.dto.command.CreateAddressCommand> captor =
                ArgumentCaptor.forClass(
                        com.parosurvivors.serviya.profiles.application.dto.command.CreateAddressCommand.class);
        verify(addressServicePort).createAddress(captor.capture());
        assertThat(captor.getValue().city()).isEqualTo("Bogota");
        assertThat(snapshot.id()).isEqualTo(3L);
    }

    @Test
    void createOffererProfileAndUpdateMainAddressDelegate() {
        when(offererProfileServicePort.createOffererProfile(7L))
                .thenReturn(OffererProfile.builder().id(4L).userId(7L).build());

        assertThat(facade.createOffererProfile(7L).id()).isEqualTo(4L);

        facade.updateMainAddress(7L, 5L);
        verify(userProfileServicePort).updateMainAddress(7L, 5L);
    }

    @Test
    void patchProfileMapsAndDelegates() {
        UserProfile updated = UserProfile.builder()
                .id(1L).userId(7L).fullName("Nuevo Nombre").profileType(ProfileType.NATURAL).build();
        when(userProfileServicePort.patchProfile(any())).thenReturn(updated);

        ProfileSnapshot snapshot = facade.patchProfile(new com.parosurvivors.serviya.profiles.api.dto.UpdateProfileCommand(
                7L, "Nuevo Nombre", "3001234567", "foto.png", "bio"));

        assertThat(snapshot.fullName()).isEqualTo("Nuevo Nombre");
        assertThat(snapshot.phoneNumber()).isNull();
    }

    @Test
    void findUserIdsByFullNameDelegatesToProfilePersistence() {
        when(userProfilePersistencePort.findUserIdsByFullName("ana")).thenReturn(List.of(1L, 2L));

        assertThat(facade.findUserIdsByFullName("ana")).containsExactly(1L, 2L);
    }

    @Test
    void findUserIdsWithinDistanceDelegatesToAddressPersistence() {
        when(addressPersistencePort.findUserIdsWithinDistance(4.6, -74.1, 10.0))
                .thenReturn(List.of(3L));

        assertThat(facade.findUserIdsWithinDistance(4.6, -74.1, 10.0))
                .containsExactly(3L);
    }
}
