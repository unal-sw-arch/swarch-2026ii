package com.parosurvivors.notifications.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

/**
 * Compatibilidad con los tokens que emite el monolito ({@code JwtService}): HS256 con clave =
 * SHA-256(JWT_SECRET), subject = userId, claim {@code roles}.
 */
public class JwtTokenVerifierTest {

    public static final String SECRET = "serviya-test-jwt-secret-for-notifications-strong-32chars";

    /** Emite un token EXACTAMENTE como lo hace el JwtService del monolito. */
    public static String monolithToken(String secret, long userId, List<String> roles, long ttlMillis) throws Exception {
        byte[] key = MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("roles", roles)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + ttlMillis))
                .signWith(Keys.hmacShaKeyFor(key))
                .compact();
    }

    @Test
    void acceptsMonolithToken_andExposesUserIdAndRoles() throws Exception {
        Authentication auth = new JwtTokenVerifier(SECRET)
                .resolve(monolithToken(SECRET, 42L, List.of("CLIENT"), 60_000)).orElseThrow();

        assertThat(auth.getPrincipal()).isEqualTo(42L);
        assertThat(auth.getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_CLIENT");
    }

    @Test
    void rejectsTokenSignedWithAnotherSecret() throws Exception {
        String foreign = monolithToken("another-strong-secret-0123456789-abcdefgh", 42L, List.of(), 60_000);

        assertThat(new JwtTokenVerifier(SECRET).resolve(foreign)).isEmpty();
    }

    @Test
    void rejectsExpiredToken() throws Exception {
        assertThat(new JwtTokenVerifier(SECRET).resolve(monolithToken(SECRET, 42L, List.of(), -1_000))).isEmpty();
    }

    @Test
    void refusesToStartWithoutStrongSecret() {
        assertThatThrownBy(() -> new JwtTokenVerifier("")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new JwtTokenVerifier("change-me-change-me-change-me-change-me"))
                .isInstanceOf(IllegalStateException.class);
    }
}
