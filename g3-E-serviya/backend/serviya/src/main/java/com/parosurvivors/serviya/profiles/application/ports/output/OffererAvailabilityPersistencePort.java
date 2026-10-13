package com.parosurvivors.serviya.profiles.application.ports.output;

import com.parosurvivors.serviya.profiles.domain.OffererAvailability;

import java.util.List;
import java.util.Optional;

public interface OffererAvailabilityPersistencePort {
    OffererAvailability save(OffererAvailability availability);
    OffererAvailability update(OffererAvailability availability);
    List<OffererAvailability> saveAll(List<OffererAvailability> availabilities);
    Optional<OffererAvailability> findById(Long id);
    List<OffererAvailability> findByOffererId(Long offererId);
    void deleteById(Long id);
    void deleteByOffererId(Long offererId);
}
