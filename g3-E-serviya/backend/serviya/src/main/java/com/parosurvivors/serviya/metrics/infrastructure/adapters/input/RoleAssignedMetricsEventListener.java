package com.parosurvivors.serviya.metrics.infrastructure.adapters.input;

import com.parosurvivors.serviya.metrics.application.ports.input.ClientMetricsServicePort;
import com.parosurvivors.serviya.metrics.application.ports.input.OffererMetricsServicePort;
import com.parosurvivors.serviya.shared.events.domain.RoleAssignedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Adaptador de entrada que inicializa las métricas 1-a-1 cuando un usuario adquiere un rol
 * (RF-010/011 auto-asignación, RF-065 asignación por admin). Así el módulo de usuarios nunca llama
 * directamente al de métricas: se mantiene el desacople por eventos.
 *
 * <p>Se escucha en fase {@link TransactionPhase#BEFORE_COMMIT} (a diferencia de los demás listeners
 * de métricas, que van en AFTER_COMMIT): la regla de negocio exige que la asignación del rol y la
 * creación de sus métricas ocurran en la MISMA transacción, de modo que si algo falla no quede un
 * rol sin métricas ni métricas sin rol.</p>
 *
 * <p>El rol ADMIN no tiene métricas asociadas: se ignora.</p>
 */
@Component
@RequiredArgsConstructor
public class RoleAssignedMetricsEventListener {

    private final OffererMetricsServicePort offererMetricsServicePort;
    private final ClientMetricsServicePort clientMetricsServicePort;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onRoleAssigned(RoleAssignedEvent event) {
        switch (event.roleName()) {
            case "OFFERER" -> offererMetricsServicePort.initializeMetrics(event.userId());
            case "CLIENT" -> clientMetricsServicePort.initializeMetrics(event.userId());
            default -> {
                // ADMIN (u otros) no llevan métricas de desempeño.
            }
        }
    }
}
