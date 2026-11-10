package com.parosurvivors.gateway.error;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Entry point 401 del gateway (T3.7): traduce el fallo de autenticación al cuerpo 4.4 con un
 * {@link GatewayErrorCode} concreto y la cabecera {@code WWW-Authenticate}.
 *
 * <p>El código se deduce del {@link OAuth2Error} que produce el resource server:
 * <ul>
 *   <li>sin token → {@code TOKEN_MISSING} (y {@code WWW-Authenticate: Bearer} a secas);</li>
 *   <li>descripción con {@code min_iat}/revocado → {@code TOKEN_REVOKED};</li>
 *   <li>descripción con {@code expired} → {@code TOKEN_EXPIRED};</li>
 *   <li>resto (firma, iss/aud, algoritmo, malformado) → {@code TOKEN_INVALID}.</li>
 * </ul>
 * El emparejado por texto es pragmático: los mensajes de Spring Security/Nimbus no están localizados.</p>
 */
public class GatewayAuthenticationEntryPoint implements ServerAuthenticationEntryPoint {

    private final GatewayErrorWriter writer;

    public GatewayAuthenticationEntryPoint(GatewayErrorWriter writer) {
        this.writer = writer;
    }

    @Override
    public Mono<Void> commence(ServerWebExchange exchange, AuthenticationException ex) {
        if (ex instanceof OAuth2AuthenticationException oauthEx) {
            OAuth2Error error = oauthEx.getError();
            String description = error.getDescription() == null ? "" : error.getDescription().toLowerCase();
            GatewayErrorCode code;
            String message;
            if (description.contains("min_iat") || description.contains("revocad")) {
                code = GatewayErrorCode.TOKEN_REVOKED;
                message = "La sesión fue revocada; vuelve a iniciar sesión.";
            } else if (description.contains("expired")) {
                code = GatewayErrorCode.TOKEN_EXPIRED;
                message = "El token de acceso expiró; refresca la sesión.";
            } else {
                code = GatewayErrorCode.TOKEN_INVALID;
                message = "El token de acceso no es válido.";
            }
            String errorCode = error.getErrorCode() == null ? "invalid_token" : error.getErrorCode();
            String wwwAuthenticate = "Bearer error=\"" + errorCode + "\", "
                    + "error_description=\"" + message + "\", "
                    + "error_uri=\"https://tools.ietf.org/html/rfc6750#section-3.1\"";
            return writer.write(exchange, HttpStatus.UNAUTHORIZED, code, message, wwwAuthenticate);
        }
        // Sin token (o credenciales ausentes): reto Bearer a secas.
        return writer.write(exchange, HttpStatus.UNAUTHORIZED, GatewayErrorCode.TOKEN_MISSING,
                "Se requiere autenticación.", "Bearer");
    }
}
