package com.parosurvivors.serviya.users.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.parosurvivors.serviya.users.application.dto.result.IssuedToken;
import com.parosurvivors.serviya.users.domain.RoleName;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.Jwts;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * El access token (RF-001/RF-002) se firma con ES256 (clave privada) y lleva los claims del contrato §4.1
 * (Fase 1). Desde la Fase 4 {@link JwtService} es solo emisor; la verificación la hace el resource server
 * estándar (ver {@code SecurityConfig}), por eso aquí solo se prueba la emisión. El fail-fast de las claves
 * vive en {@link JwtKeysTest}.
 */
class JwtServiceTest {

    private JwtKeys keys;
    private JwtService service;

    @BeforeEach
    void setUp() {
        JwtProperties props = JwtTestSupport.properties();
        keys = new JwtKeys(props);
        service = new JwtService(keys, props);
    }

    @Test
    @SuppressWarnings("unchecked")
    void issuedTokenCarriesContractClaims() {
        IssuedToken issued = service.issue(42L, List.of(RoleName.CLIENT, RoleName.OFFERER));

        Jws<Claims> jws = Jwts.parser()
                .verifyWith(keys.publicKey())
                .build()
                .parseSignedClaims(issued.token());
        JwsHeader header = jws.getHeader();
        Claims claims = jws.getPayload();

        assertThat(header.getAlgorithm()).isEqualTo("ES256");
        assertThat(header.getKeyId()).isEqualTo(JwtTestSupport.KEY_ID);
        assertThat(header.getType()).isEqualTo("at+jwt");
        assertThat(claims.getIssuer()).isEqualTo(JwtTestSupport.ISSUER);
        assertThat(claims.getAudience()).contains(JwtTestSupport.AUDIENCE);
        assertThat(claims.getSubject()).isEqualTo("42");
        assertThat(claims.getId()).isNotBlank(); // jti
        assertThat(claims.getIssuedAt()).isNotNull();
        assertThat(claims.getExpiration()).isAfter(claims.getIssuedAt());
        assertThat((List<String>) claims.get("roles", List.class)).containsExactly("CLIENT", "OFFERER");
    }
}
