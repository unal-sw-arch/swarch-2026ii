package com.parosurvivors.serviya.users.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.parosurvivors.serviya.users.application.dto.result.IssuedToken;
import com.parosurvivors.serviya.users.domain.RoleName;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

/**
 * El JWT (RF-001/RF-002) se firma con JWT_SECRET y valida los roles. No debe existir un
 * secreto por defecto: si JWT_SECRET falta o viene vacia, el arranque falla (fail-fast, B6).
 */
class JwtServiceTest {

    private static final String SECRET = "test-jwt-secret-0123456789abcdef0123456789abcdef";

    @Test
    void issuesAndResolvesTokenRoundTrip() {
        JwtService service = new JwtService(SECRET, 3600000L);

        IssuedToken issued = service.issue(42L, List.of(RoleName.CLIENT, RoleName.OFFERER));

        Optional<Authentication> resolved = service.resolve(issued.token());
        assertThat(resolved).isPresent();
        assertThat(resolved.get().getPrincipal()).isEqualTo(42L);
        assertThat(resolved.get().getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_CLIENT", "ROLE_OFFERER");
    }

    @Test
    void rejectsInvalidToken() {
        JwtService service = new JwtService(SECRET, 3600000L);

        assertThat(service.resolve("not-a-jwt")).isEmpty();
    }

    @Test
    void failsFastWhenSecretMissing() {
        assertThatThrownBy(() -> new JwtService("", 3600000L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
    }

    @Test
    void failsFastWhenSecretBlank() {
        assertThatThrownBy(() -> new JwtService("   ", 3600000L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
    }
}