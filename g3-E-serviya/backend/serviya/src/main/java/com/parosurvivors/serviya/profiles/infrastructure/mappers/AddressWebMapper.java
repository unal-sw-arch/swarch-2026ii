package com.parosurvivors.serviya.profiles.infrastructure.mappers;

import com.parosurvivors.serviya.profiles.application.dto.command.CreateAddressCommand;
import com.parosurvivors.serviya.profiles.application.dto.command.UpdateAddressCommand;
import com.parosurvivors.serviya.profiles.application.dto.result.CoordinatesResult;
import com.parosurvivors.serviya.profiles.domain.Address;
import com.parosurvivors.serviya.profiles.infrastructure.dto.form.CreateAddressForm;
import com.parosurvivors.serviya.profiles.infrastructure.dto.form.UpdateAddressForm;
import com.parosurvivors.serviya.profiles.infrastructure.dto.response.AddressResponse;
import com.parosurvivors.serviya.profiles.infrastructure.dto.response.AddressVerificationResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * Mapper web (MapStruct) de direcciones: Form->Command y dominio/Result->Response.
 * TODO: completar mapeos donde difieran los nombres de campo.
 */
@Mapper(componentModel = "spring")
public interface AddressWebMapper {

    @Mapping(target = "userId", source = "userId")
    CreateAddressCommand toCommand(CreateAddressForm form, Long userId);

    @Mapping(target = "addressId", source = "addressId")
    UpdateAddressCommand toCommand(UpdateAddressForm form, Long addressId);

    AddressResponse toResponse(Address address);

    List<AddressResponse> toResponses(List<Address> addresses);

    @Mapping(target = "valid", source = "valid")
    @Mapping(target = "latitude", source = "coordinates.latitude")
    @Mapping(target = "longitude", source = "coordinates.longitude")
    AddressVerificationResponse toVerificationResponse(boolean valid, CoordinatesResult coordinates);
}
