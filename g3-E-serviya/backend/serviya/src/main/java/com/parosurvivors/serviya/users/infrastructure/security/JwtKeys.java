package com.parosurvivors.serviya.users.infrastructure.security;

import java.security.KeyFactory;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import org.springframework.stereotype.Component;

/**
 * Carga y custodia el par de claves EC P-256 usado para firmar/verificar los access tokens ES256
 * (Fase 1, {@code guia-implementacion-auth.md}). Es infraestructura: aquí viven los detalles de JCA;
 * ni {@code application} ni {@code domain} conocen claves.
 *
 * <p><b>Fail-fast (T1.3):</b> el arranque se aborta si falta una clave, no decodifica, no es EC P-256 o
 * falta el {@code kid}. Así nunca se firma con material inválido ni con una clave conocida. Reemplaza el
 * {@code JWT_SECRET} HS256 anterior (secreto simétrico compartido) por una clave privada que SOLO tiene
 * el emisor; los verificadores usan la pública (del JWKS).
 *
 * <p>Acepta la clave en base64 del DER (una línea, cómodo para {@code .env}/variables de entorno) o en
 * PEM con armadura: se le quita la armadura y los espacios y se decodifica igual.
 */
@Component
public class JwtKeys {

    private static final int P256_FIELD_SIZE = 256;

    private final ECPrivateKey privateKey;
    private final ECPublicKey publicKey;
    private final String keyId;

    public JwtKeys(JwtProperties properties) {
        this.privateKey = loadPrivateKey(properties.getPrivateKey());
        this.publicKey = loadPublicKey(properties.getPublicKey());
        this.keyId = requireKeyId(properties.getKeyId());
    }

    public ECPrivateKey privateKey() {
        return privateKey;
    }

    public ECPublicKey publicKey() {
        return publicKey;
    }

    public String keyId() {
        return keyId;
    }

    private static ECPrivateKey loadPrivateKey(String value) {
        byte[] der = decode(value, "JWT_PRIVATE_KEY");
        try {
            var key = KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(der));
            if (!(key instanceof ECPrivateKey ecKey) || !isP256(ecKey.getParams().getCurve().getField().getFieldSize())) {
                throw invalidKey("JWT_PRIVATE_KEY", "no es una clave EC P-256");
            }
            return ecKey;
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw invalidKey("JWT_PRIVATE_KEY", "no se pudo leer como PKCS#8 EC P-256 (" + ex.getMessage() + ")");
        }
    }

    private static ECPublicKey loadPublicKey(String value) {
        byte[] der = decode(value, "JWT_PUBLIC_KEY");
        try {
            var key = KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(der));
            if (!(key instanceof ECPublicKey ecKey) || !isP256(ecKey.getParams().getCurve().getField().getFieldSize())) {
                throw invalidKey("JWT_PUBLIC_KEY", "no es una clave EC P-256");
            }
            return ecKey;
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw invalidKey("JWT_PUBLIC_KEY", "no se pudo leer como X.509 EC P-256 (" + ex.getMessage() + ")");
        }
    }

    private static boolean isP256(int fieldSize) {
        return fieldSize == P256_FIELD_SIZE;
    }

    private static String requireKeyId(String keyId) {
        if (keyId == null || keyId.isBlank()) {
            throw invalidKey("JWT_KEY_ID", "está vacío (el JWKS lo necesita como 'kid' para seleccionar la clave)");
        }
        return keyId.trim();
    }

    /** Quita armadura PEM y espacios, decodifica base64. Fail-fast si falta o no decodifica. */
    private static byte[] decode(String value, String name) {
        if (value == null || value.isBlank()) {
            throw invalidKey(name, "no está definida. El arranque se aborta para no firmar/verificar sin clave");
        }
        String cleaned = value.replaceAll("-----BEGIN[^-]*-----", "")
                .replaceAll("-----END[^-]*-----", "")
                .replaceAll("\\s", "");
        try {
            return Base64.getDecoder().decode(cleaned);
        } catch (IllegalArgumentException ex) {
            throw invalidKey(name, "no es base64 válido (DER o PEM de una clave EC P-256)");
        }
    }

    private static IllegalStateException invalidKey(String name, String reason) {
        return new IllegalStateException(name + " " + reason
                + ". Genera un par EC P-256 (openssl ecparam -name prime256v1 ...) y configúralo en .env o el entorno.");
    }
}
