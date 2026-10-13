package com.parosurvivors.serviya.profiles.infrastructure.adapters.input;

import com.parosurvivors.serviya.profiles.application.ports.output.OffererProfilePersistencePort;
import com.parosurvivors.serviya.profiles.domain.OffererProfile;
import com.parosurvivors.serviya.shared.events.domain.RoleAssignedEvent;
import com.parosurvivors.serviya.users.domain.RoleName;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Adaptador de entrada que crea la fila 1-a-1 de {@code offerer_profiles} cuando un usuario adquiere
 * el rol OFFERER (RF-010 auto-asignacion, RF-065 concesion por admin).
 *
 * <p>Se crea una plantilla VACIA (campos en blanco): el oferente la completa despues con sus datos
 * publicos (RF-015). Asi el modulo de usuarios no necesita conocer al de perfiles: reacciona al
 * evento, igual que hacen las metricas.</p>
 *
 * <p>Fase {@link TransactionPhase#BEFORE_COMMIT}: la plantilla se crea dentro de la MISMA transaccion
 * que la asignacion del rol, como exige la atomicidad de RF-010. Idempotente: si ya existe, no hace nada
 * (p. ej. si el usuario fue oferente antes, perdio el rol y lo readquiere, conserva su perfil previo).</p>
 */
@Component
@RequiredArgsConstructor
public class RoleAssignedProfileEventListener {

    private final OffererProfilePersistencePort offererProfilePersistencePort;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onRoleAssigned(RoleAssignedEvent event) {
        if (!RoleName.OFFERER.name().equals(event.roleName())) {
            return; // CLIENT/ADMIN no tienen perfil de oferente.
        }
        if (offererProfilePersistencePort.findByUserId(event.userId()).isPresent()) {
            return; // idempotente.
        }
        offererProfilePersistencePort.save(OffererProfile.builder()
                .userId(event.userId())
                .whatsappNumber("")
                .publicDescription("")
                .specialty("")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());
    }
}
