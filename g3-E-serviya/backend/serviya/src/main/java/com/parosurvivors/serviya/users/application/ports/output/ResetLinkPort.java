package com.parosurvivors.serviya.users.application.ports.output;

/**
 * Puerto de salida para construir el enlace de recuperación que viaja en el correo (RF-003).
 *
 * <p>La URL base del frontend es configuración del BACKEND y el enlace se arma aquí dentro: nunca se
 * acepta una URL que venga en la petición. Si el cliente pudiera elegir el destino, un atacante haría
 * un reset a nombre de la víctima apuntando a su propio dominio, y el correo — legítimo y firmado por
 * nosotros — le entregaría el token de un solo uso (<i>password reset poisoning</i>). El endpoint
 * {@code /password-reset} es público, así que ese abuso no requiere ninguna credencial.</p>
 *
 * <p>La capa de aplicación depende de esta abstracción; el detalle (leer la propiedad y concatenar)
 * vive en infraestructura, igual que {@link TokenProviderPort} respecto a JWT.</p>
 */
public interface ResetLinkPort {

    /** Devuelve la URL completa del formulario de nueva contraseña, con el token en el query string. */
    String buildResetLink(String rawToken);
}
