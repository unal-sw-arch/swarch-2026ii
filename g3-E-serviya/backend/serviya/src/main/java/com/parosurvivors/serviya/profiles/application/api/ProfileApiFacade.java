package com.parosurvivors.serviya.profiles.application.api;

import com.parosurvivors.serviya.profiles.api.ProfileCommandApi;
import com.parosurvivors.serviya.profiles.api.ProfileQueryApi;
import com.parosurvivors.serviya.profiles.api.dto.AddressSnapshot;
import com.parosurvivors.serviya.profiles.api.dto.CreateAddressCommand;
import com.parosurvivors.serviya.profiles.api.dto.CreateProfileCommand;
import com.parosurvivors.serviya.profiles.api.dto.OffererAvailabilitySnapshot;
import com.parosurvivors.serviya.profiles.api.dto.OffererProfileSnapshot;
import com.parosurvivors.serviya.profiles.api.dto.OffererProfileSummarySnapshot;
import com.parosurvivors.serviya.profiles.api.dto.ProfileSnapshot;
import com.parosurvivors.serviya.profiles.api.dto.UpdateProfileCommand;
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
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Implementacion de los contratos publicos de profiles sobre los casos de uso existentes.
 *
 * <p>Las lecturas usan los puertos de persistencia del propio modulo. Las vistas compuestas
 * (perfil publico con rating, vitrina con servicios) viven en el BFF.</p>
 */
@Component
@RequiredArgsConstructor
public class ProfileApiFacade implements ProfileQueryApi, ProfileCommandApi {

    private final UserProfilePersistencePort userProfilePersistencePort;
    private final OffererProfilePersistencePort offererProfilePersistencePort;
    private final AddressPersistencePort addressPersistencePort;
    private final OffererAvailabilityPersistencePort offererAvailabilityPersistencePort;

    private final UserProfileServicePort userProfileServicePort;
    private final OffererProfileServicePort offererProfileServicePort;
    private final AddressServicePort addressServicePort;

    // ---------------------------------------------------------------------
    // ProfileQueryApi
    // ---------------------------------------------------------------------

    @Override
    public Optional<ProfileSnapshot> findProfile(Long userId) {
        return userProfilePersistencePort.findByUserId(userId).map(this::toProfileSnapshot);
    }

    @Override
    public List<ProfileSnapshot> findProfiles(Collection<Long> userIds) {
        return userProfilePersistencePort.findByUserIdIn(userIds).stream()
                .map(this::toProfileSnapshot)
                .toList();
    }

    @Override
    public Optional<OffererProfileSnapshot> findOffererProfile(Long userId) {
        return offererProfilePersistencePort.findByUserId(userId).map(this::toOffererProfileSnapshot);
    }

    @Override
    public Optional<OffererProfileSummarySnapshot> getOffererSummary(Long userId) {
        Optional<UserProfile> userProfile = userProfilePersistencePort.findByUserId(userId);
        Optional<OffererProfile> offererProfile = offererProfilePersistencePort.findByUserId(userId);
        if (userProfile.isEmpty() || offererProfile.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new OffererProfileSummarySnapshot(
                userId,
                userProfile.get().getFullName(),
                userProfile.get().getProfilePhotoUrl(),
                offererProfile.get().getSpecialty()));
    }

    @Override
    public Optional<AddressSnapshot> findAddress(Long addressId) {
        return addressPersistencePort.findById(addressId).map(this::toAddressSnapshot);
    }

    @Override
    public List<AddressSnapshot> getAddresses(Long userId) {
        return addressPersistencePort.findByUserId(userId).stream()
                .map(this::toAddressSnapshot)
                .toList();
    }

    @Override
    public List<OffererAvailabilitySnapshot> getAvailabilities(Long offererId) {
        return offererAvailabilityPersistencePort.findByOffererId(offererId).stream()
                .map(this::toAvailabilitySnapshot)
                .toList();
    }

    @Override
    public List<Long> findUserIdsByFullName(String nameFragment) {
        return userProfilePersistencePort.findUserIdsByFullName(nameFragment);
    }

    @Override
    public List<Long> findUserIdsWithinDistance(double latitude, double longitude, double maxDistanceKm) {
        return addressPersistencePort.findUserIdsWithinDistance(
                latitude, longitude, maxDistanceKm);
    }

    // ---------------------------------------------------------------------
    // ProfileCommandApi
    // ---------------------------------------------------------------------

    @Override
    public ProfileSnapshot createProfile(CreateProfileCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("CreateProfileCommand is required");
        }
        UserProfile created = userProfileServicePort.createProfile(
                new com.parosurvivors.serviya.profiles.application.dto.command.CreateUserProfileCommand(
                        command.userId(),
                        command.fullName(),
                        command.documentType(),
                        command.documentNumber(),
                        command.phoneNumber(),
                        toProfileType(command.profileType())));
        return toProfileSnapshot(created);
    }

    @Override
    public OffererProfileSnapshot createOffererProfile(Long userId) {
        return toOffererProfileSnapshot(offererProfileServicePort.createOffererProfile(userId));
    }

    @Override
    public AddressSnapshot createAddress(CreateAddressCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("CreateAddressCommand is required");
        }
        Address created = addressServicePort.createAddress(
                new com.parosurvivors.serviya.profiles.application.dto.command.CreateAddressCommand(
                        command.userId(),
                        command.addressLine(),
                        command.city(),
                        command.latitude(),
                        command.longitude()));
        return toAddressSnapshot(created);
    }

    @Override
    public void updateMainAddress(Long userId, Long addressId) {
        userProfileServicePort.updateMainAddress(userId, addressId);
    }

    @Override
    public ProfileSnapshot patchProfile(UpdateProfileCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("UpdateProfileCommand is required");
        }
        UserProfile updated = userProfileServicePort.patchProfile(
                new com.parosurvivors.serviya.profiles.application.dto.command.UpdateProfileCommand(
                        command.userId(),
                        command.fullName(),
                        command.phone(),
                        command.photoUrl(),
                        command.description()));
        return toProfileSnapshot(updated);
    }

    // ---------------------------------------------------------------------
    // Mapping
    // ---------------------------------------------------------------------

    private ProfileSnapshot toProfileSnapshot(UserProfile profile) {
        return new ProfileSnapshot(
                profile.getId(),
                profile.getUserId(),
                profile.getFullName(),
                profile.getDocumentType(),
                profile.getDocumentNumber(),
                profile.getPhoneNumber(),
                profile.getPrimaryAddressId(),
                profile.getProfilePhotoUrl(),
                profile.getBio(),
                profile.getProfileType() == null ? null : profile.getProfileType().name(),
                profile.getCreatedAt());
    }

    private OffererProfileSnapshot toOffererProfileSnapshot(OffererProfile profile) {
        return new OffererProfileSnapshot(
                profile.getId(),
                profile.getUserId(),
                profile.getWhatsappNumber(),
                profile.getPublicDescription(),
                profile.getSpecialty(),
                profile.getCreatedAt(),
                profile.getUpdatedAt());
    }

    private AddressSnapshot toAddressSnapshot(Address address) {
        return new AddressSnapshot(
                address.getId(),
                address.getUserId(),
                address.getAddressLine(),
                address.getCity(),
                address.getLatitude(),
                address.getLongitude(),
                address.getCreatedAt());
    }

    private OffererAvailabilitySnapshot toAvailabilitySnapshot(OffererAvailability availability) {
        return new OffererAvailabilitySnapshot(
                availability.getId(),
                availability.getOffererId(),
                availability.getWeekDay() == null ? 0 : availability.getWeekDay(),
                availability.getStartTime(),
                availability.getEndTime(),
                Boolean.TRUE.equals(availability.getActive()));
    }

    private ProfileType toProfileType(String profileType) {
        if (profileType == null) {
            throw new IllegalArgumentException("profileType is required");
        }
        try {
            return ProfileType.valueOf(profileType);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unknown profileType: " + profileType, ex);
        }
    }
}
