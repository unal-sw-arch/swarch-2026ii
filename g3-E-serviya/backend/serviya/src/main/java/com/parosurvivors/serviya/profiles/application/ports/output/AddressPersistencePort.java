package com.parosurvivors.serviya.profiles.application.ports.output;

import com.parosurvivors.serviya.profiles.domain.Address;

import java.util.List;
import java.util.Optional;

public interface AddressPersistencePort {
    Address save(Address address);
    Address update(Address address);
    Optional<Address> findById(Long id);
    List<Address> findByUserId(Long userId);
    List<Long> findUserIdsWithinDistance(double latitude, double longitude, double maxDistanceKm);
    void deleteById(Long id);
}
