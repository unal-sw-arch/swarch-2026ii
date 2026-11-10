package com.parosurvivors.gateway.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Utilidad de test: genera una clave EC P-256, publica su JWKS y firma access tokens ES256 a medida
 * (válidos, caducados, de otra audiencia/emisor, con roles, o con un algoritmo inválido) para probar el
 * decoder del gateway de extremo a extremo sin depender del monolito real.
 */
public final class TestJwks {

    public static final String KEY_ID = "test-key-1";

    private final ECKey signingKey;

    public TestJwks() {
        try {
            this.signingKey = new ECKeyGenerator(Curve.P_256).keyID(KEY_ID).generate();
        } catch (JOSEException ex) {
            throw new IllegalStateException("No se pudo generar la clave EC de test", ex);
        }
    }

    /** JWKS con SOLO la clave pública (lo que publicaría el emisor). */
    public String jwksJson() {
        return new JWKSet(signingKey.toPublicJWK()).toString();
    }

    /** Token ES256 válido con {@code typ=at+jwt}, firmado con la clave de este JWKS. */
    public String validToken(String issuer, String audience, String subject, List<String> roles) {
        Instant now = Instant.now();
        return es256(issuer, audience, subject, roles, "at+jwt", now, now.plusSeconds(900));
    }

    /** Token ES256 ya caducado. */
    public String expiredToken(String issuer, String audience, String subject, List<String> roles) {
        Instant past = Instant.now().minusSeconds(3600);
        return es256(issuer, audience, subject, roles, "at+jwt", past, past.plusSeconds(60));
    }

    private String es256(String issuer, String audience, String subject, List<String> roles,
                         String typ, Instant iat, Instant exp) {
        try {
            return sign(new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(KEY_ID)
                            .type(new JOSEObjectType(typ)).build(),
                    new ECDSASigner(signingKey), issuer, audience, subject, roles, iat, exp);
        } catch (JOSEException ex) {
            throw new IllegalStateException("No se pudo firmar el token ES256 de test", ex);
        }
    }

    /** Token firmado con HS256 (algoritmo prohibido): el decoder debe rechazarlo. */
    public String hs256Token(String issuer, String audience, String subject) {
        try {
            byte[] secret = "0123456789abcdef0123456789abcdef".getBytes(); // 256 bits para HS256
            Instant now = Instant.now();
            return sign(new JWSHeader.Builder(JWSAlgorithm.HS256).type(new JOSEObjectType("at+jwt")).build(),
                    new MACSigner(secret), issuer, audience, subject, List.of("CLIENT"), now, now.plusSeconds(900));
        } catch (JOSEException ex) {
            throw new IllegalStateException("No se pudo firmar el token HS256 de test", ex);
        }
    }

    private String sign(JWSHeader header, JWSSigner signer, String issuer, String audience,
                        String subject, List<String> roles, Instant iat, Instant exp) throws JOSEException {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .audience(audience)
                .subject(subject)
                .claim("roles", roles)
                .issueTime(Date.from(iat))
                .expirationTime(Date.from(exp))
                .jwtID(UUID.randomUUID().toString())
                .build();
        SignedJWT jwt = new SignedJWT(header, claims);
        jwt.sign(signer);
        return jwt.serialize();
    }
}
