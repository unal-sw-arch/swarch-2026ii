package com.parosurvivors.serviya.metrics.application.services;

import com.parosurvivors.serviya.metrics.application.ports.input.OffererMetricsServicePort;
import com.parosurvivors.serviya.metrics.application.ports.output.OffererMetricsPersistencePort;
import com.parosurvivors.serviya.metrics.domain.OffererMetrics;
import com.parosurvivors.serviya.requests.domain.RequestStatus;
import com.parosurvivors.serviya.shared.exceptions.ResourceNotFoundException;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Métricas agregadas del usuario como oferente. Consulta (rating, contadores de estado, tags) +
 * escritura por eventos: el feedback de servicio alimenta rating/comentarios/tags del oferente
 * dueño, y {@code RequestStatusChangedEvent} alimenta los contadores de estado. Cada apply* corre
 * en su propia transacción (AFTER_COMMIT del publicador), con find-or-create.
 */
@Component
@RequiredArgsConstructor
public class OffererMetricsService implements OffererMetricsServicePort {

    private final OffererMetricsPersistencePort offererMetricsPersistencePort;

    @Override
    public OffererMetrics getAllMetrics(Long offererId) {
        return offererMetricsPersistencePort.findByOffererId(offererId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Offerer metrics not found for offererId: " + offererId));
    }

    @Override
    public OffererMetrics getMainMetrics(Long offererId) {
        return offererMetricsPersistencePort.findByOffererId(offererId)
                .orElse(OffererMetrics.builder().offererId(offererId).build());
    }

    /**
     * RF-010/065: fila de métricas en cero al adquirir el rol OFFERER. A diferencia de los apply*
     * (que corren AFTER_COMMIT en transacción propia), esta se une a la transacción del llamador
     * para que "asignar rol + inicializar métricas" sea atómico.
     */
    @Override
    @Transactional
    public void initializeMetrics(Long offererId) {
        if (offererMetricsPersistencePort.findByOffererId(offererId).isEmpty()) {
            // updated_at es NOT NULL en el esquema y el builder no lo rellena (los apply* lo ponen via touch()).
            offererMetricsPersistencePort.save(OffererMetrics.builder()
                    .offererId(offererId)
                    .updatedAt(LocalDateTime.now())
                    .build());
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void applyServiceFeedbackSubmitted(Long offererId, Integer rating, boolean hasComment,
            int positiveTags, int negativeTags) {
        OffererMetrics metrics = findOrCreate(offererId);
        if (rating != null) {
            metrics.registerRating(rating);
        }
        if (hasComment) {
            metrics.incrementComments();
        }
        if (positiveTags > 0) {
            metrics.addPositiveTags(positiveTags);
        }
        if (negativeTags > 0) {
            metrics.addNegativeTags(negativeTags);
        }
        persist(metrics);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void applyServiceFeedbackReverted(Long offererId, Integer rating, boolean hasComment,
            int positiveTags, int negativeTags) {
        offererMetricsPersistencePort.findByOffererId(offererId).ifPresent(metrics -> {
            if (rating != null) {
                metrics.removeRating(rating);
            }
            if (hasComment) {
                metrics.decrementComments();
            }
            if (positiveTags > 0) {
                metrics.removePositiveTags(positiveTags);
            }
            if (negativeTags > 0) {
                metrics.removeNegativeTags(negativeTags);
            }
            offererMetricsPersistencePort.update(metrics);
        });
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void applyRequestStatusChanged(Long offererId, RequestStatus newStatus) {
        OffererMetrics metrics = findOrCreate(offererId);
        switch (newStatus) {
            case ACCEPTED -> metrics.incrementAccepted();
            case COMPLETED -> metrics.incrementCompleted();
            case CANCELLED -> metrics.incrementCancelled();
            case NOT_PROVIDED -> metrics.incrementNotProvided();
            // PENDING / REJECTED / PRESUMABLY_COMPLETED / RESCHEDULED no tienen contador de oferente.
            default -> {
                return;
            }
        }
        persist(metrics);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void incrementRequestsReceived(Long offererId) {
        OffererMetrics metrics = findOrCreate(offererId);
        metrics.incrementRequestsReceived();
        persist(metrics);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void incrementRescheduleProposalsSent(Long offererId) {
        OffererMetrics metrics = findOrCreate(offererId);
        metrics.incrementRescheduleProposalsSent();
        persist(metrics);
    }

    private OffererMetrics findOrCreate(Long offererId) {
        return offererMetricsPersistencePort.findByOffererId(offererId)
                .orElseGet(() -> OffererMetrics.builder().offererId(offererId).build());
    }

    private void persist(OffererMetrics metrics) {
        if (metrics.getId() == null) {
            offererMetricsPersistencePort.save(metrics);
        } else {
            offererMetricsPersistencePort.update(metrics);
        }
    }
}
