package com.parosurvivors.notifications.infrastructure.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.List;
import java.util.Optional;

/**
 * Verifica los JWT que EMITE el monolito (identity): HS256, subject = userId, claim {@code roles}. Usa el
 * mismo {@code JWT_SECRET} con la misma derivación SHA-256 que {@code JwtService} del monolito. Este
 * servicio solo VERIFICA, nunca emite.
 *
 * <p>Diferencia consciente con el monolito: no revalida contra la tabla {@code users} si la cuenta sigue
 * activa (esa tabla no es de este servicio). Un usuario baneado conserva el acceso a SU bandeja hasta que
 * expira el token (TTL {@code JWT_EXPIRATION_MS}). Ver ESTADO §7.5 "autenticación distribuida".</p>
 */
@Component
public class JwtTokenVerifier {

    private final SecretKey key;

    public JwtTokenVerifier(@Value("${serviya.security.jwt-secret:}") String secret) {
        this.key = Keys.hmacShaKeyFor(SecretKeys.requireStrongSecret("JWT_SECRET", secret));
    }

    /** Authentication con el userId como principal y {@code ROLE_<NAME>} como authorities; vacío si es inválido. */
    @SuppressWarnings("unchecked")
    public Optional<Authentication> resolve(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            Long userId = Long.valueOf(claims.getSubject());
            List<String> roles = claims.get("roles", List.class);
            List<SimpleGrantedAuthority> authorities = (roles == null ? List.<String>of() : roles).stream()
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                    .toList();

            return Optional.of(new UsernamePasswordAuthenticationToken(userId, null, authorities));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }
}
