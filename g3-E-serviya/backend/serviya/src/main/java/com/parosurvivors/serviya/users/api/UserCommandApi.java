package com.parosurvivors.serviya.users.api;

import com.parosurvivors.serviya.users.api.dto.CreateUserAccountCommand;
import com.parosurvivors.serviya.users.api.dto.UserSnapshot;

/**
 * Comandos publicos de identity.
 *
 * <p>Los roles viajan como {@code String} y se validan contra el enum interno en la implementacion.
 * Los metodos no exponen excepciones de dominio del modulo.</p>
 */
public interface UserCommandApi {

    UserSnapshot createUserAccount(CreateUserAccountCommand command);

    void changeEmail(Long userId, String newEmail);

    void changePassword(Long userId, String currentPassword, String newPassword);

    void banUser(Long userId, String reason);

    void unbanUser(Long userId);

    void assignRole(Long userId, String roleName);

    void revokeRole(Long userId, String roleName);

    void removeRole(Long userId, Long roleId);

    void deleteUser(Long userId);
}
