package com.parosurvivors.serviya.users.infrastructure.adapters.input;

import com.parosurvivors.serviya.shared.lock.DistributedLock;
import com.parosurvivors.serviya.users.application.ports.input.PasswordResetTokenServicePort;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Adaptador de entrada por TIEMPO: borra los tokens de recuperación ya caducados (RF-003).
 *
 * <p>Un token expirado no sirve para nada — ni siquiera para auditoría, porque su valor en claro nunca
 * se guardó — y dejarlos acumularse solo hace crecer la tabla y su índice único. Fino: solo enruta hacia
 * el puerto de entrada, igual que {@code RequestMaintenanceScheduler}.
 * Requiere {@code @EnableScheduling} (ya activado en la clase de arranque).</p>
 *
 * <p>Por defecto se ejecuta a diario de madrugada: es mantenimiento, no tiene ninguna urgencia (un token
 * caducado ya lo rechaza la validación aunque siga en la tabla).</p>
 *
 * <p>Envuelto en {@link DistributedLock} (clave {@code lock:{sched:reset-token-cleanup}}) para que, con
 * ≥2 instancias, solo una lo ejecute por disparo (deuda #1). Si Redis falla, se omite (D-08). TTL más
 * corto (~5 min): es un único {@code DELETE} masivo, muy rápido.</p>
 */
@Component
@RequiredArgsConstructor
public class PasswordResetCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetCleanupScheduler.class);
    /** Recurso del lock distribuido (clave Redis {@code lock:{sched:reset-token-cleanup}}; ver catálogo). */
    private static final String LOCK_RESET_TOKEN_CLEANUP = "sched:reset-token-cleanup";
    private static final Duration LOCK_TTL = Duration.ofMinutes(5);

    private final PasswordResetTokenServicePort passwordResetTokenService;
    private final DistributedLock distributedLock;

    @Scheduled(cron = "${serviya.password-reset.cron.cleanup-expired:0 30 3 * * *}")
    public void deleteExpiredTokens() {
        distributedLock.runIfAcquired(LOCK_RESET_TOKEN_CLEANUP, LOCK_TTL, () -> {
            int deleted = passwordResetTokenService.deleteExpiredTokens(LocalDateTime.now());
            if (deleted > 0) {
                log.info("Deleted {} expired password reset tokens", deleted);
            }
        });
    }
}
