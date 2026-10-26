package com.parosurvivors.serviya.profiles.application.services;

import com.parosurvivors.serviya.profiles.application.dto.command.CreateAddressCommand;
import com.parosurvivors.serviya.profiles.application.dto.command.UpdateAddressCommand;
import com.parosurvivors.serviya.profiles.application.dto.result.CoordinatesResult;
import com.parosurvivors.serviya.profiles.application.mappers.AddressCommandMapper;
import com.parosurvivors.serviya.profiles.application.ports.input.AddressServicePort;
import com.parosurvivors.serviya.profiles.application.ports.output.AddressPersistencePort;
import com.parosurvivors.serviya.profiles.application.ports.output.GeocodingPort;
import com.parosurvivors.serviya.profiles.application.ports.output.UserProfilePersistencePort;
import com.parosurvivors.serviya.profiles.domain.Address;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.parosurvivors.serviya.shared.exceptions.ResourceNotFoundException;
import com.parosurvivors.serviya.shared.exceptions.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Implementación de AddressServicePort con geocodificación real vía Nominatim (OpenStreetMap).
 * Ver documents/project-structure/estructura-servicios.docx.
 */
@Component
@RequiredArgsConstructor
public class AddressService implements AddressServicePort {

    private final UserProfilePersistencePort profilePersistencePort;
    private final AddressPersistencePort persistencePort;
    private final AddressCommandMapper commandMapper;
    private final GeocodingPort geocodingPort;

    @Override
    public List<Address> getUserAddresses(Long userId) {
        return new ArrayList<>(persistencePort.findByUserId(userId));
    }

    @Override
    public Address createAddress(CreateAddressCommand command) {
        LocalDateTime now = LocalDateTime.now();
        Address address = commandMapper.toDomain(command);
        address.setCreatedAt(now);
        return persistencePort.save(address);
    }

    @Override
    public void deleteAddress(Long addressId, Long requesterId, boolean isAdmin) {
        Address address = persistencePort.findById(addressId)
                .orElseThrow(() -> new ResourceNotFoundException("Dirección no encontrada con id: " + addressId));
        requireOwnership(address, requesterId, isAdmin);
        persistencePort.deleteById(addressId);
    }

    @Override
    public Address updateAddress(UpdateAddressCommand command, Long requesterId, boolean isAdmin) {
        Address address = persistencePort.findById(command.addressId())
                .orElseThrow(() -> new ResourceNotFoundException("Dirección no encontrada con id: " + command.addressId()));

        requireOwnership(address, requesterId, isAdmin);

        commandMapper.updateFromCommand(command, address);

        return persistencePort.update(address);
    }

    @Override
    public boolean verifyAddress(String addressLine, String city) {
        return geocodingPort.geocode(addressLine, city).isPresent();
    }

    @Override
    public CoordinatesResult getCoordinates(String addressLine, String city) {
        return geocodingPort.geocode(addressLine, city)
                .orElse(new CoordinatesResult(null, null));
    }

    /**
     * El actor debe ser el dueño de la direccion, o un admin (IDOR: B2).
     * Mismo patron "propietario O admin" que {@code MarketplaceService.requireOwnership}.
     */
    private void requireOwnership(Address address, Long requesterId, boolean isAdmin) {
        if (isAdmin) {
            return;
        }
        if (requesterId == null || !requesterId.equals(address.getUserId())) {
            throw new UnauthorizedException("El usuario no es el propietario de la dirección");
        }
    }
}
