package com.parosurvivors.serviya.users.infrastructure.security;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuración de la emisión de tokens de acceso ES256 (Fase 1 del plan de centralización de auth,
 * {@code documents/microservicios/guia-implementacion-auth.md}). Externalizada a {@code serviya.security.jwt.*}
 * para ajustarla sin recompilar y para que los secretos (claves) se inyecten por entorno.
 *
 * <pre>
 * serviya:
 *   security:
 *     jwt:
 *       private-key: ${JWT_PRIVATE_KEY}   # clave EC P-256 privada (PKCS#8, base64 DER o PEM). SOLO el emisor.
 *       public-key:  ${JWT_PUBLIC_KEY}    # clave EC P-256 pública (X.509 SPKI, base64 DER o PEM). Se publica en el JWKS.
 *       key-id:      ${JWT_KEY_ID}        # 'kid' del header; selecciona la clave en el JWKS (rotación).
 *       issuer:      ${JWT_ISSUER}        # 'iss': URL lógica estable del emisor (sobrevive a la extracción de identidad).
 *       audience:    ${JWT_AUDIENCE}      # 'aud': destinatario de la API (marketplace-api).
 *       access-ttl:  ${JWT_ACCESS_TTL}    # vida del access token (p. ej. 15m). El refresh llega en la Fase 2.
 *       typ:         at+jwt               # 'typ' del header (RFC 9068): evita confundir otros JWT con un access token.
 * </pre>
 */
@Component
@Getter
@Setter
@ConfigurationProperties(prefix = "serviya.security.jwt")
public class JwtProperties {

    /** Clave EC P-256 privada (PKCS#8), en base64 del DER o PEM con armadura. Solo la tiene el emisor. */
    private String privateKey;

    /** Clave EC P-256 pública (X.509 SubjectPublicKeyInfo), en base64 del DER o PEM. Se publica en el JWKS. */
    private String publicKey;

    /** Identificador de la clave ({@code kid}); permite publicar/rotar varias claves en el JWKS. */
    private String keyId;

    /** Emisor ({@code iss}): URL lógica estable, no la URL interna del servicio. */
    private String issuer;

    /** Audiencia ({@code aud}): identificador de la API destinataria. */
    private String audience;

    /** Vida del access token (formato de duración de Spring, p. ej. {@code 15m}). */
    private Duration accessTtl = Duration.ofMinutes(15);

    /** Tipo del token en el header JOSE ({@code typ}); por defecto {@code at+jwt} (RFC 9068). */
    private String typ = "at+jwt";

    // --- Refresh token (Fase 2) ---

    /** Vida ABSOLUTA de la familia de refresh (no se extiende al rotar). Por defecto 30 días. */
    private Duration refreshTtl = Duration.ofDays(30);

    /** Ventana de gracia de reuso: dentro de ella no se rota (varias pestañas). Por defecto 15 s. */
    private Duration refreshGrace = Duration.ofSeconds(15);

    /** Nombre de la cookie HttpOnly que transporta el refresh. */
    private String refreshCookieName = "serviya_refresh";

    /** Path de la cookie: la acota a los endpoints de auth (refresh + logout). */
    private String refreshCookiePath = "/api/v1/auth";

    /** {@code Secure}: en dev sobre http debe ser false; en prod (https), true. */
    private boolean refreshCookieSecure = false;

    /** {@code SameSite}: Strict por defecto (defensa CSRF principal, P2.2). */
    private String refreshCookieSameSite = "Strict";

    // --- Revocación min_iat (Fase 2, Axis B) ---

    /**
     * Tolerancia de reloj que se suma a {@link #accessTtl} para el TTL de la clave {@code min_iat}
     * (contrato 4.3). El TTL = vida máxima del access + este margen: pasado ese plazo ningún token anterior
     * al corte sigue vivo, así que la clave se autolimpia. Por defecto 60 s.
     */
    private Duration revocationClockSkew = Duration.ofSeconds(60);
}
