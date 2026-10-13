package com.parosurvivors.serviya.profiles.application.ports.input;

import com.parosurvivors.serviya.profiles.application.dto.command.CreateAddressCommand;
import com.parosurvivors.serviya.profiles.application.dto.command.UpdateAddressCommand;
import com.parosurvivors.serviya.profiles.application.dto.result.CoordinatesResult;
import com.parosurvivors.serviya.profiles.domain.Address;

import java.util.List;

/**
 * Puerto de entrada de AddressService. Recibe Commands y devuelve dominio (Address) o Result;
 * nunca tipos web. Ver documents/project-structure/estructura-servicios.docx (módulo 2).
 */
public interface AddressServicePort {

    List<Address> getUserAddresses(Long userId);

    Address createAddress(CreateAddressCommand command);

    void deleteAddress(Long addressId, Long requesterId, boolean isAdmin);

    Address updateAddress(UpdateAddressCommand command, Long requesterId, boolean isAdmin);

    boolean verifyAddress(String addressLine, String city);

    CoordinatesResult getCoordinates(String addressLine, String city);
}
