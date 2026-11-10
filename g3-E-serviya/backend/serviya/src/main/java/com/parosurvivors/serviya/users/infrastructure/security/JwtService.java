package com.parosurvivors.serviya.users.infrastructure.security;

import com.parosurvivors.serviya.users.application.dto.result.IssuedToken;
import com.parosurvivors.serviya.users.application.ports.output.TokenProviderPort;
import com.parosurvivors.serviya.users.domain.RoleName;
import io.jsonwebtoken.Jwts;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Adaptador de salida que implementa {@link TokenProviderPort} emitiendo access tokens <b>ES256</b>
 * (jjwt; Fase 1 de {@code guia-implementacion-auth.md}). SOLO el emisor firma (con la clave privada de
 * {@link JwtKeys}); los verificadores usan la pública (publicada en el JWKS).
 *
 * <p>El token lleva los claims del contrato (§4.1): header {@code kid}+{@code typ}, y
 * {@code iss}/{@code aud}/{@code sub}/{@code roles}/{@code jti}/{@code iat}/{@code exp}. El {@code sub} es el
 * userId; {@code roles} la lista de roles.
 *
 * <p>Desde la Fase 4 esta clase es <b>solo emisor</b>: la verificación la hace el resource server estándar
 * (ver {@code SecurityConfig}), ya no un {@code resolve()} propio. Seguirá siendo el emisor hasta la Fase 8.
 */
@Component
public class JwtService implements TokenProviderPort {

    private final JwtKeys keys;
    private final JwtProperties properties;

    public JwtService(JwtKeys keys, JwtProperties properties) {
        this.keys = keys;
        this.properties = properties;
    }

    @Override
    public IssuedToken issue(Long userId, List<RoleName> roles) {
        Instant now = Instant.now();
        Instant expiration = now.plus(properties.getAccessTtl());
        List<String> roleNames = roles == null ? List.of() : roles.stream().map(Enum::name).toList();

        String token = Jwts.builder()
                .header().keyId(keys.keyId()).type(properties.getTyp()).and()
                .issuer(properties.getIssuer())
                .audience().add(properties.getAudience()).and()
                .subject(String.valueOf(userId))
                .claim("roles", roleNames)
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiration))
                .signWith(keys.privateKey(), Jwts.SIG.ES256)
                .compact();

        return new IssuedToken(token, LocalDateTime.ofInstant(expiration, ZoneId.systemDefault()));
    }
}
