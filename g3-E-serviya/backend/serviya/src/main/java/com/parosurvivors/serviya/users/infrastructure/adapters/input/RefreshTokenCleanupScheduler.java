package com.parosurvivors.serviya.users.infrastructure.adapters.input;

import com.parosurvivors.serviya.shared.lock.DistributedLock;
import com.parosurvivors.serviya.users.application.ports.input.TokenSessionServicePort;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Adaptador de entrada por TIEMPO: borra los refresh tokens ya expirados o revocados (Fase 2).
 *
 * <p>Una familia caducada o revocada ya no sirve ni para auditoría (el valor en claro nunca se guardó) y
 * dejarla acumularse solo hace crecer la tabla. A diario de madrugada: es mantenimiento sin urgencia (un
 * refresh caducado/revocado ya lo rechaza la rotación aunque siga en la tabla).
 *
 * <p>Envuelto en {@link DistributedLock} ({@code lock:{sched:refresh-token-cleanup}}) para que con ≥2
 * instancias solo una lo ejecute por disparo (deuda #1). Si Redis falla, se omite (D-08). TTL corto: es
 * un único {@code DELETE} masivo.</p>
 */
@Component
@RequiredArgsConstructor
public class RefreshTokenCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenCleanupScheduler.class);
    private static final String LOCK_REFRESH_TOKEN_CLEANUP = "sched:refresh-token-cleanup";
    private static final Duration LOCK_TTL = Duration.ofMinutes(5);

    private final TokenSessionServicePort tokenSessionService;
    private final DistributedLock distributedLock;

    @Scheduled(cron = "${serviya.refresh-token.cron.cleanup:0 35 3 * * *}")
    public void deleteExpiredOrRevokedTokens() {
        distributedLock.runIfAcquired(LOCK_REFRESH_TOKEN_CLEANUP, LOCK_TTL, () -> {
            int deleted = tokenSessionService.deleteExpiredOrRevoked(LocalDateTime.now());
            if (deleted > 0) {
                log.info("Deleted {} expired/revoked refresh tokens", deleted);
            }
        });
    }
}
