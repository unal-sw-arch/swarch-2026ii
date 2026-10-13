package com.parosurvivors.serviya.users.application.ports.input;

import com.parosurvivors.serviya.users.application.dto.command.ConfirmPasswordResetCommand;
import com.parosurvivors.serviya.users.application.dto.command.LoginCommand;
import com.parosurvivors.serviya.users.application.dto.command.RegisterUserCommand;
import com.parosurvivors.serviya.users.application.dto.command.RequestPasswordResetCommand;
import com.parosurvivors.serviya.users.application.dto.result.AuthResult;

/**
 * Puerto de entrada de UserAuthenticationService — orquestador de login, registro
 * y recuperación de contraseña. Recibe Commands (capa aplicación) y devuelve AuthResult;
 * nunca tipos web (Form/Response).
 * Ver documents/project-structure/estructura-servicios.docx (módulo 1).
 */
public interface UserAuthenticationServicePort {

    AuthResult login(LoginCommand command);

    AuthResult register(RegisterUserCommand command);

    /**
     * RF-010/011: el usuario autenticado adquiere un rol publico (CLIENT u OFFERER) y recibe un JWT
     * NUEVO que ya incluye ese rol.
     *
     * <p>Los roles viajan como claim del token, asi que sin re-emitirlo el usuario tendria que volver
     * a iniciar sesion para usar su rol nuevo. Devolver aqui el token actualizado es lo que hace
     * efectivo el criterio de aceptacion "acceso inmediato a todas sus funciones".</p>
     */
    AuthResult acquireRole(Long userId, String roleName);

    void requestPasswordReset(RequestPasswordResetCommand command);

    /**
     * RF-003: comprueba si el enlace todavía sirve, para decidir si se pinta el formulario de nueva
     * contraseña o un mensaje de enlace caducado. NO consume el token.
     *
     * <p>Colapsa a propósito el detalle: el {@code TokenValidationResult} (inexistente / expirado /
     * usado) se queda dentro de la aplicación y hacia fuera solo hay "sirve" o "no sirve". Distinguir
     * los tres casos le diría a un atacante si un token existió alguna vez.</p>
     *
     * @throws com.parosurvivors.serviya.shared.exceptions.InvalidStateException si el enlace ya no sirve
     */
    void validatePasswordResetToken(String rawToken);

    void confirmPasswordReset(ConfirmPasswordResetCommand command);
}
