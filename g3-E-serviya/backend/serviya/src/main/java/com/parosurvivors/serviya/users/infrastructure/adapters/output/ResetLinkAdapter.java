package com.parosurvivors.serviya.users.infrastructure.adapters.output;

import com.parosurvivors.serviya.users.application.ports.output.ResetLinkPort;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Adaptador de salida de {@link ResetLinkPort}: arma el enlace a partir de la URL base configurada
 * en {@code serviya.frontend.reset-url} (variable de entorno {@code FRONTEND_RESET_URL}).
 *
 * <p>La URL apunta al FRONTEND, no al backend: quien procesa el enlace es la vista de React, que lee
 * el token del query string y lo reenvía al backend al enviar la nueva contraseña. Por eso el valor
 * cambia por entorno (Vite :5173 en dev, :8081 con el frontend dockerizado).</p>
 */
@Component
public class ResetLinkAdapter implements ResetLinkPort {

    /** URL base del formulario de nueva contrasena en el frontend. Cambia por entorno. */
    @Value("${serviya.frontend.reset-url}")
    private String resetBaseUrl;

    @Override
    public String buildResetLink(String rawToken) {
        // El token ya es Base64 url-safe, pero se codifica igualmente: si algun dia cambia el alfabeto
        // del token, el enlace no se rompe en silencio.
        String encodedToken = URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
        String separator = resetBaseUrl.contains("?") ? "&" : "?";
        return resetBaseUrl + separator + "token=" + encodedToken;
    }
}
