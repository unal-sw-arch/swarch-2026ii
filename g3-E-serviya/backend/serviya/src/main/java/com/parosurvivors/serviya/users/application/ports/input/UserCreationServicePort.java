package com.parosurvivors.serviya.users.application.ports.input;

import com.parosurvivors.serviya.users.application.dto.command.CreateUserAccountCommand;
import com.parosurvivors.serviya.users.domain.User;

/**
 * Puerto de entrada de UserCreationService — flujo compartido de creación de usuario
 * reutilizado por register (visitante) y createUserByAdmin (admin).
 * Ver documents/project-structure/estructura-servicios.docx (módulo 1).
 */
public interface UserCreationServicePort {

    User createUserAccount(CreateUserAccountCommand command);
}
