package com.parosurvivors.serviya.users.application.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;

import com.parosurvivors.serviya.users.application.ports.output.RefreshTokenPort;
import com.parosurvivors.serviya.users.application.ports.output.TokenRevocationPort;
import java.time.Instant;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Revocación total de sesión (Fase 2, Axis B): combina la revocación durable (familias de refresh en BD) y
 * la inmediata ({@code min_iat}), en ese orden, con el corte {@code ahora + 1 s} del contrato 4.3.
 */
@ExtendWith(MockitoExtension.class)
class SessionRevocationServiceTest {

    @Mock private RefreshTokenPort refreshTokenPort;
    @Mock private TokenRevocationPort tokenRevocationPort;

    @Test
    void revokeAllSessions_revokesRefreshFamilies_thenWritesMinIat_atNowPlusOneSecond() {
        SessionRevocationService service = new SessionRevocationService(refreshTokenPort, tokenRevocationPort);
        Instant before = Instant.now();

        service.revokeAllSessions(42L);

        Instant after = Instant.now();

        // (1) Primero la garantía durable: revoca TODAS las familias de refresh del usuario.
        // (2) Luego min_iat. El orden importa (la BD es autoritativa; min_iat es mejor esfuerzo).
        InOrder order = inOrder(refreshTokenPort, tokenRevocationPort);
        order.verify(refreshTokenPort).revokeAllForUser(eq(42L), any(LocalDateTime.class));
        ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
        order.verify(tokenRevocationPort).revokeAllIssuedBefore(eq(42L), cutoff.capture());

        // Corte = ahora + 1 s (contrato 4.3): cubre tokens emitidos en el mismo segundo del evento.
        assertThat(cutoff.getValue())
                .isAfterOrEqualTo(before.plusSeconds(1))
                .isBeforeOrEqualTo(after.plusSeconds(1));
    }

    @Test
    void revokeAllSessions_alwaysWritesMinIat_evenWithNoLiveRefreshFamilies() {
        // revokeAllForUser por defecto devuelve 0 (sin familias vivas); min_iat se escribe igual, porque el
        // usuario puede tener un access token corto aún válido aunque ya no tenga refresh.
        SessionRevocationService service = new SessionRevocationService(refreshTokenPort, tokenRevocationPort);

        service.revokeAllSessions(7L);

        verify(tokenRevocationPort).revokeAllIssuedBefore(eq(7L), any(Instant.class));
    }
}
