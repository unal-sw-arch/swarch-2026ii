package com.parosurvivors.serviya.users.infrastructure.security;

import com.parosurvivors.serviya.users.application.dto.result.IssuedToken;
import com.parosurvivors.serviya.users.application.ports.output.TokenProviderPort;
import com.parosurvivors.serviya.users.domain.RoleName;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * Adaptador de salida que implementa {@link TokenProviderPort} usando JWT (jjwt, HS256).
 * Tambien expone {@link #resolve(String)} para el {@link JwtAuthenticationFilter} (uso interno
 * de infraestructura). El subject del token es el userId y lleva un claim {@code roles}.
 *
 * <p>El secreto se lee de la propiedad {@code JWT_SECRET} (cargada desde .env.example por DotEnvConfig o
 * desde la variable de entorno en Docker); se deriva con SHA-256 para garantizar 256 bits
 * independientemente de su longitud. No existe un valor por defecto: si la propiedad falta o viene
 * vacia, el arranque se aborta (fail-fast) para no firmar tokens con una clave conocida por el publico.
 */
@Component
public class JwtService implements TokenProviderPort {

    private final SecretKey key;
    private final long expirationMs;

    public JwtService(
            @Value("${JWT_SECRET:}") String secret,
            @Value("${JWT_EXPIRATION_MS:3600000}") long expirationMs) {
        this.key = requireJwtSecret(secret);
        this.expirationMs = expirationMs;
    }

    private static SecretKey requireJwtSecret(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "JWT_SECRET no esta definida. El arranque se aborta para no firmar tokens "
                            + "con un secreto por defecto/inseguro. Configurala en .env (o en el entorno).");
        }
        // Rechazar valores débiles/conocidos que podrían venir de .env.example copiado sin modificar
        if (isWeakJwtSecret(secret)) {
            throw new IllegalStateException(
                    "JWT_SECRET usa un valor inseguro (por defecto/de ejemplo). "
                            + "Genera un secreto seguro (mín 32 chars aleatorios) y configuralo en .env o variables de entorno.");
        }
        return Keys.hmacShaKeyFor(sha256(secret));
    }

    private static boolean isWeakJwtSecret(String secret) {
        String lower = secret.toLowerCase();
        return lower.contains("dev-")
                || lower.contains("change-me")
                || lower.contains("example")
                || lower.contains("default")
                || lower.equals("secret")
                || secret.length() < 32;
    }

    @Override
    public IssuedToken issue(Long userId, List<RoleName> roles) {
        Instant now = Instant.now();
        Instant expiration = now.plusMillis(expirationMs);
        List<String> roleNames = roles == null ? List.of() : roles.stream().map(Enum::name).toList();

        String token = Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("roles", roleNames)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiration))
                .signWith(key)
                .compact();

        return new IssuedToken(token, LocalDateTime.ofInstant(expiration, ZoneId.systemDefault()));
    }

    /**
     * Valida la firma/expiracion y construye un {@link Authentication} con el userId como principal
     * y los roles como authorities ({@code ROLE_<NAME>}). Vacio si el token es invalido o expiro.
     */
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

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}
