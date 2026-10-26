package com.parosurvivors.serviya.requests.infrastructure.adapters.input;

import com.parosurvivors.serviya.requests.application.ports.input.RequestMaintenanceServicePort;
import com.parosurvivors.serviya.shared.lock.DistributedLock;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Adaptador de entrada por TIEMPO: dispara las tareas de mantenimiento de solicitudes/propuestas
 * ({@link RequestMaintenanceServicePort}) en un cron configurable. Solo enruta hacia el puerto de
 * entrada (sin lógica), análogo a un controller REST pero con {@code @Scheduled} como disparador.
 * Requiere {@code @EnableScheduling} (activado en la clase de arranque). Crons por defecto: cada hora.
 *
 * <p>Cada tarea se envuelve en un {@link DistributedLock} (clave {@code lock:{sched:...}}) para que, con
 * ≥2 instancias, solo una la ejecute por disparo (deuda #1). Son idempotentes: si Redis falla, la tarea
 * se omite (D-08) y se retoma en el siguiente cron. El TTL es la red de seguridad si la instancia muere
 * a mitad; holgado frente a la duración real (ver catálogo de claves).</p>
 */
@Component
@RequiredArgsConstructor
public class RequestMaintenanceScheduler {

    // Recursos de los locks distribuidos (claves Redis lock:{<recurso>}; ver catálogo de claves).
    private static final String LOCK_REJECT_EXPIRED_PENDING = "sched:reject-expired-pending";
    private static final String LOCK_MARK_STALE_ACCEPTED = "sched:mark-stale-accepted";
    private static final String LOCK_REJECT_EXPIRED_PROPOSALS = "sched:reject-expired-proposals";
    private static final String LOCK_FINALIZE_UNCONFIRMED = "sched:finalize-unconfirmed";
    private static final Duration LOCK_TTL = Duration.ofMinutes(10);

    private final RequestMaintenanceServicePort maintenanceService;
    private final DistributedLock distributedLock;

    @Scheduled(cron = "${serviya.maintenance.cron.reject-expired-pending:0 0 * * * *}")
    public void rejectExpiredPendingRequests() {
        distributedLock.runIfAcquired(LOCK_REJECT_EXPIRED_PENDING, LOCK_TTL,
                maintenanceService::rejectExpiredPendingRequests);
    }

    @Scheduled(cron = "${serviya.maintenance.cron.mark-stale-accepted:0 10 * * * *}")
    public void markStaleAcceptedAsNotProvided() {
        distributedLock.runIfAcquired(LOCK_MARK_STALE_ACCEPTED, LOCK_TTL,
                maintenanceService::markStaleAcceptedAsNotProvided);
    }

    @Scheduled(cron = "${serviya.maintenance.cron.reject-expired-proposals:0 20 * * * *}")
    public void rejectExpiredProposals() {
        distributedLock.runIfAcquired(LOCK_REJECT_EXPIRED_PROPOSALS, LOCK_TTL,
                maintenanceService::rejectExpiredProposals);
    }

    @Scheduled(cron = "${serviya.maintenance.cron.finalize-unconfirmed:0 30 * * * *}")
    public void finalizeUnconfirmedCompletions() {
        distributedLock.runIfAcquired(LOCK_FINALIZE_UNCONFIRMED, LOCK_TTL,
                maintenanceService::finalizeUnconfirmedCompletions);
    }
}
