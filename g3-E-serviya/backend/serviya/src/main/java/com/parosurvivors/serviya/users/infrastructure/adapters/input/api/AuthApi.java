package com.parosurvivors.serviya.users.infrastructure.adapters.input.api;

import com.parosurvivors.serviya.users.infrastructure.dto.form.ConfirmPasswordResetForm;
import com.parosurvivors.serviya.users.infrastructure.dto.form.LoginForm;
import com.parosurvivors.serviya.users.infrastructure.dto.form.RegisterUserForm;
import com.parosurvivors.serviya.users.infrastructure.dto.form.RequestPasswordResetForm;
import com.parosurvivors.serviya.users.infrastructure.dto.form.ValidatePasswordResetTokenForm;
import com.parosurvivors.serviya.users.infrastructure.dto.response.AuthResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

/**
 * Documentacion OpenAPI/Swagger del controlador de autenticacion (modulo 1).
 * Rutas publicas bajo /api/v1/auth/**. Ver documents/project-structure/estructura-endpoints.md (seccion 1).
 * Convencion: docs de metodo aqui; las anotaciones de binding y @Parameter van en el controller.
 */
@Tag(name = "Autenticacion",
        description = "Registro, login y recuperacion de contrasena (endpoints publicos). "
                + "Todos llevan rate limiting: al agotar la cuota responden 429 con cabecera Retry-After. "
                + "Los que reciben un correo (login y solicitud de recuperacion) llevan ademas una cuota "
                + "por direccion, para que repartir el ataque entre varias IPs no lo sortee.")
public interface AuthApi {

    @Operation(summary = "Registrar un nuevo usuario (CLIENT u OFFERER)",
            description = "Valida la entrada y delega la creacion comun. Devuelve el JWT. RF-002, RF-004.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Usuario registrado, devuelve JWT"),
            @ApiResponse(responseCode = "400", description = "Datos invalidos"),
            @ApiResponse(responseCode = "409", description = "Email ya registrado")
    })
    ResponseEntity<AuthResponse> register(RegisterUserForm form);

    @Operation(summary = "Iniciar sesion", description = "Verifica credenciales y devuelve el JWT. RF-001.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Login exitoso, devuelve JWT"),
            @ApiResponse(responseCode = "401", description = "Credenciales invalidas")
    })
    ResponseEntity<AuthResponse> login(LoginForm form);

    @Operation(summary = "Rotar la sesion (refresh token)",
            description = "Emite un access token nuevo a partir del refresh token que viaja en la cookie "
                    + "HttpOnly (Fase 2). Fuera de la ventana de gracia rota tambien el refresh (uno nuevo "
                    + "en la misma familia, misma caducidad absoluta) y devuelve una cookie nueva. Relee "
                    + "cuenta y roles desde BD. Reusar un refresh ya rotado revoca la familia entera. "
                    + "No requiere el access token.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Access token nuevo (y cookie de refresh rotada)"),
            @ApiResponse(responseCode = "401", description = "Refresh ausente, invalido, caducado, reusado o cuenta inactiva")
    })
    ResponseEntity<AuthResponse> refresh(String refreshCookie, String userAgent);

    @Operation(summary = "Cerrar sesion",
            description = "Revoca la familia del refresh token presentado en la cookie y la limpia. "
                    + "Idempotente: responde 204 aunque no haya cookie.")
    @ApiResponse(responseCode = "204", description = "Sesion cerrada (cookie de refresh eliminada)")
    ResponseEntity<Void> logout(String refreshCookie);

    @Operation(summary = "Cerrar sesion en todos los dispositivos",
            description = "Revoca TODAS las familias de refresh del usuario autenticado y escribe min_iat "
                    + "(el gateway invalida de inmediato cualquier access token ya emitido). A diferencia de "
                    + "/logout, exige el access token (la identidad sale del JWT, no de la cookie) y limpia "
                    + "la cookie de este dispositivo. RF — gestion de sesiones.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Todas las sesiones cerradas"),
            @ApiResponse(responseCode = "401", description = "Sin access token valido")
    })
    ResponseEntity<Void> logoutAll();

    @Operation(summary = "Solicitar recuperacion de contrasena",
            description = "Genera y envia el token de recuperacion por correo. RF-003.")
    @ApiResponse(responseCode = "202", description = "Solicitud aceptada (respuesta generica por seguridad)")
    ResponseEntity<Void> requestPasswordReset(RequestPasswordResetForm form);

    @Operation(summary = "Comprobar si un enlace de recuperacion sigue siendo valido",
            description = "Lo llama la vista de nueva contrasena al montarse, para decidir si pinta el "
                    + "formulario o un aviso de enlace caducado. NO consume el token: hay clientes de "
                    + "correo que previsualizan los enlaces y lo quemarian antes de que el usuario llegue. "
                    + "El motivo del rechazo (inexistente / expirado / ya usado) nunca se distingue. "
                    + "Es un POST pese a ser de solo lectura para que el token viaje en el cuerpo y no "
                    + "quede escrito en los access logs. RF-003.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "El enlace sigue sirviendo"),
            @ApiResponse(responseCode = "400", description = "Falta el token"),
            @ApiResponse(responseCode = "409", description = "Enlace invalido, caducado o ya utilizado")
    })
    ResponseEntity<Void> validatePasswordResetToken(ValidatePasswordResetTokenForm form);

    @Operation(summary = "Confirmar recuperacion de contrasena",
            description = "Valida el token (enviado en el body, no en query) y cambia la contrasena. RF-003.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Contrasena actualizada"),
            @ApiResponse(responseCode = "409", description = "Token invalido, usado o expirado")
    })
    ResponseEntity<Void> confirmPasswordReset(ConfirmPasswordResetForm form);
}
