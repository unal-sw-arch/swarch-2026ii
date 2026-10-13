package com.parosurvivors.serviya.users.application.ports.input;

import com.parosurvivors.serviya.users.application.dto.command.ChangeEmailCommand;
import com.parosurvivors.serviya.users.application.dto.command.ChangePasswordCommand;
import com.parosurvivors.serviya.users.domain.User;

/**
 * Puerto de entrada de UserService — gestión del usuario ya existente.
 * Ver documents/project-structure/estructura-servicios.docx (módulo 1).
 */
public interface UserServicePort {

    User createUser(String email, String rawPassword);

    void changePassword(ChangePasswordCommand command);

    void changeEmail(ChangeEmailCommand command);

    /** Suspende la cuenta y notifica al usuario por doble canal (INTERNAL + EMAIL) con el motivo (RF-063). */
    void banUser(Long userId, String reason);

    /** Reactiva la cuenta y notifica al usuario por doble canal (INTERNAL + EMAIL) (RF-075). */
    void unbanUser(Long userId);
}
