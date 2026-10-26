package com.parosurvivors.serviya.profiles.application.ports.input;

import com.parosurvivors.serviya.profiles.application.dto.command.CreateUserProfileCommand;
import com.parosurvivors.serviya.profiles.application.dto.command.UpdateProfileCommand;
import com.parosurvivors.serviya.profiles.domain.UserProfile;

/**
 * Puerto de entrada de UserProfileService. Recibe Commands y devuelve dominio (UserProfile);
 * nunca tipos web. Ver documents/project-structure/estructura-servicios.docx (módulo 2).
 */
public interface UserProfileServicePort {

    UserProfile createProfile(CreateUserProfileCommand command);

    UserProfile getProfileInfo(Long userId);

    UserProfile patchProfile(UpdateProfileCommand command);

    void updateMainAddress(Long userId, Long addressId);
}
