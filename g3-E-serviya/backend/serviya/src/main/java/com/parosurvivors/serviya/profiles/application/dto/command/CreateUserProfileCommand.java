package com.parosurvivors.serviya.profiles.application.dto.command;

import com.parosurvivors.serviya.profiles.domain.ProfileType;

/**
 * Command interno del flujo de creacion de usuario: crea la fila de {@code user_profiles}
 * asociada a una cuenta recien creada (RF-002). Lo arma {@code UserCreationService} a partir
 * de los datos de perfil del formulario de registro. document/phone se cifran al persistir.
 */
public record CreateUserProfileCommand(
        Long userId,
        String fullName,
        String documentType,
        String documentNumber,
        String phoneNumber,
        ProfileType profileType) {
}
