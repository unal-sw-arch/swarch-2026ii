package com.parosurvivors.serviya.profiles.api;

import com.parosurvivors.serviya.profiles.api.dto.AddressSnapshot;
import com.parosurvivors.serviya.profiles.api.dto.CreateAddressCommand;
import com.parosurvivors.serviya.profiles.api.dto.CreateProfileCommand;
import com.parosurvivors.serviya.profiles.api.dto.OffererProfileSnapshot;
import com.parosurvivors.serviya.profiles.api.dto.ProfileSnapshot;
import com.parosurvivors.serviya.profiles.api.dto.UpdateProfileCommand;

/**
 * Comandos publicos del modulo profiles.
 */
public interface ProfileCommandApi {

    ProfileSnapshot createProfile(CreateProfileCommand command);

    OffererProfileSnapshot createOffererProfile(Long userId);

    AddressSnapshot createAddress(CreateAddressCommand command);

    void updateMainAddress(Long userId, Long addressId);

    /** Edicion parcial del perfil personal (PATCH); los campos nulos no se tocan. */
    ProfileSnapshot patchProfile(UpdateProfileCommand command);
}
