package com.parosurvivors.serviya.profiles.application.ports.input;

import com.parosurvivors.serviya.profiles.application.dto.command.UpdateOffererProfileCommand;
import com.parosurvivors.serviya.profiles.domain.OffererProfile;

/**
 * Puerto de entrada de OffererProfileService. Recibe Commands y devuelve dominio/read model;
 * nunca tipos web. Ver documents/project-structure/estructura-servicios.docx (módulo 2).
 */
public interface OffererProfileServicePort {

    OffererProfile createOffererProfile(Long userId);

    OffererProfile getPublicProfile(Long userId);

    OffererProfile patchOffererProfile(UpdateOffererProfileCommand command);
}
