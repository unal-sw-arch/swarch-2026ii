package com.parosurvivors.serviya.users.infrastructure.security;

import java.time.Duration;

/**
 * Claves EC P-256 de PRUEBA (generadas con openssl, base64 del DER) y una factoría de {@link JwtProperties}
 * para los tests de la emisión ES256 (Fase 1). No usar fuera de tests.
 */
public final class JwtTestSupport {

    public static final String PRIVATE_KEY =
            "MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQgQ34gtsVTWWij47x09RZCxR7naS3dlGJMdrHr/lVITJ2hRANCAAQK/Q+kf2OU2hnY1hV7bJELtnwBx+D8LgVmFF81c1eAkY0vt/YVQ200C0aKy6Gpd9XnWVqPBerl7uRIUcm8EM0N";
    public static final String PUBLIC_KEY =
            "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAECv0PpH9jlNoZ2NYVe2yRC7Z8Acfg/C4FZhRfNXNXgJGNL7f2FUNtNAtGisuhqXfV51lajwXq5e7kSFHJvBDNDQ==";
    public static final String KEY_ID = "test-key-1";
    public static final String ISSUER = "https://auth.serviya.test";
    public static final String AUDIENCE = "marketplace-api";

    private JwtTestSupport() {}

    public static JwtProperties properties() {
        JwtProperties p = new JwtProperties();
        p.setPrivateKey(PRIVATE_KEY);
        p.setPublicKey(PUBLIC_KEY);
        p.setKeyId(KEY_ID);
        p.setIssuer(ISSUER);
        p.setAudience(AUDIENCE);
        p.setAccessTtl(Duration.ofMinutes(15));
        p.setTyp("at+jwt");
        return p;
    }
}
