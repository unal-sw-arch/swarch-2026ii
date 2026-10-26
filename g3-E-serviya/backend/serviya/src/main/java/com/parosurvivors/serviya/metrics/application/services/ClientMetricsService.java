package com.parosurvivors.serviya.metrics.application.services;

import com.parosurvivors.serviya.metrics.application.ports.input.ClientMetricsServicePort;
import com.parosurvivors.serviya.metrics.application.ports.output.ClientMetricsPersistencePort;
import com.parosurvivors.serviya.metrics.domain.ClientMetrics;
import com.parosurvivors.serviya.requests.application.ports.input.ServiceRequestQueryServicePort;
import com.parosurvivors.serviya.requests.domain.RequestStatus;
import com.parosurvivors.serviya.shared.exceptions.ResourceNotFoundException;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Métricas agregadas del usuario como cliente. Consulta + escritura por eventos: el feedback del
 * oferente al cliente alimenta rating/comentarios/tags, y {@code RequestStatusChangedEvent} alimenta
 * los contadores de estado de las solicitudes del cliente. Cada apply* corre en su propia
 * transacción (AFTER_COMMIT del publicador), con find-or-create.
 */
@Component
@RequiredArgsConstructor
public class ClientMetricsService implements ClientMetricsServicePort {

    private final ClientMetricsPersistencePort clientMetricsPersistencePort;
    private final ServiceRequestQueryServicePort serviceRequestQueryServicePort;

    @Override
    public ClientMetrics getAllMetrics(Long clientId) {
        ClientMetrics metrics = clientMetricsPersistencePort.findByClientId(clientId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Client metrics not found for clientId: " + clientId));
        return withActiveRequests(metrics);
    }

    @Override
    public ClientMetrics getMainMetrics(Long clientId) {
        ClientMetrics metrics = clientMetricsPersistencePort.findByClientId(clientId)
                .orElse(ClientMetrics.builder().clientId(clientId).build());
        return withActiveRequests(metrics);
    }

    /**
     * Rellena {@code activeRequests} con el conteo real de solicitudes no terminales del cliente
     * (consulta a service_requests vía el módulo de requests). Valor de solo lectura, no persistido.
     */
    private ClientMetrics withActiveRequests(ClientMetrics metrics) {
        metrics.setActiveRequests((int) serviceRequestQueryServicePort.countActiveClientRequests(metrics.getClientId()));
        return metrics;
    }

    /**
     * RF-011/065: fila de métricas en cero al adquirir el rol CLIENT. A diferencia de los apply*
     * (que corren AFTER_COMMIT en transacción propia), esta se une a la transacción del llamador
     * para que "asignar rol + inicializar métricas" sea atómico. Idempotente.
     */
    @Override
    @Transactional
    public void initializeMetrics(Long clientId) {
        if (clientMetricsPersistencePort.findByClientId(clientId).isEmpty()) {
            // updated_at es NOT NULL en el esquema y el builder no lo rellena (los apply* lo ponen via touch()).
            clientMetricsPersistencePort.save(ClientMetrics.builder()
                    .clientId(clientId)
                    .updatedAt(LocalDateTime.now())
                    .build());
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void applyClientFeedbackSubmitted(Long clientId, Integer rating, boolean hasComment,
            int positiveTags, int negativeTags) {
        ClientMetrics metrics = findOrCreate(clientId);
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
    public void applyClientFeedbackReverted(Long clientId, Integer rating, boolean hasComment,
            int positiveTags, int negativeTags) {
        clientMetricsPersistencePort.findByClientId(clientId).ifPresent(metrics -> {
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
            clientMetricsPersistencePort.update(metrics);
        });
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void applyRequestStatusChanged(Long clientId, RequestStatus newStatus) {
        ClientMetrics metrics = findOrCreate(clientId);
        switch (newStatus) {
            case ACCEPTED -> metrics.incrementAccepted();
            case COMPLETED -> metrics.incrementCompleted();
            case CANCELLED -> metrics.incrementCancelled();
            case RESCHEDULED -> metrics.incrementRescheduled();
            case NOT_PROVIDED -> metrics.incrementNotProvided();
            // PENDING / REJECTED / PRESUMABLY_COMPLETED no tienen contador de cliente.
            default -> {
                return;
            }
        }
        persist(metrics);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void incrementRequestsSent(Long clientId) {
        ClientMetrics metrics = findOrCreate(clientId);
        metrics.incrementRequestsSent();
        persist(metrics);
    }

    private ClientMetrics findOrCreate(Long clientId) {
        return clientMetricsPersistencePort.findByClientId(clientId)
                .orElseGet(() -> ClientMetrics.builder().clientId(clientId).build());
    }

    private void persist(ClientMetrics metrics) {
        if (metrics.getId() == null) {
            clientMetricsPersistencePort.save(metrics);
        } else {
            clientMetricsPersistencePort.update(metrics);
        }
    }
}
