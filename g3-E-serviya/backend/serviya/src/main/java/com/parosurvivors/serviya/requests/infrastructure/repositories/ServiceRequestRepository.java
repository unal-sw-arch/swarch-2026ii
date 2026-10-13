package com.parosurvivors.serviya.requests.infrastructure.repositories;

import com.parosurvivors.serviya.requests.domain.RequestStatus;
import com.parosurvivors.serviya.requests.infrastructure.entities.ServiceRequestEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ServiceRequestRepository extends JpaRepository<ServiceRequestEntity, Long> {
    List<ServiceRequestEntity> findByClientId(Long clientId);

    List<ServiceRequestEntity> findByOffererId(Long offererId);

    /**
     * Solicitudes en las que el usuario participa (cliente u oferente) y cuyo estado está en la lista dada.
     */
    @Query("SELECT r FROM ServiceRequestEntity r WHERE (r.clientId = :userId OR r.offererId = :userId)"
            + " AND r.status IN :statuses")
    List<ServiceRequestEntity> findByParticipantAndStatusIn(@Param("userId") Long userId,
                                                            @Param("statuses") List<RequestStatus> statuses);

    List<ServiceRequestEntity> findByServiceId(Long serviceId);

    List<ServiceRequestEntity> findByStatus(RequestStatus status);

    Optional<ServiceRequestEntity> findByPreviousRequestId(Long previousRequestId);

    //These next two methods are meant for the Agenda feature. Will return future requests, and not completed.
    List<ServiceRequestEntity> findByClientIdAndScheduledDateAfter(Long clientId, LocalDateTime tomorrowOrNow);

    List<ServiceRequestEntity> findByOffererIdAndScheduledDateAfter(Long offererId, LocalDateTime tomorrowOrNow);

    // Mantenimiento por tiempo (tareas @Scheduled): solicitudes vencidas por fecha en un estado dado.
    List<ServiceRequestEntity> findByStatusAndScheduledDateBefore(RequestStatus status, LocalDateTime cutoff);

    List<ServiceRequestEntity> findByStatusAndCompletedAtBefore(RequestStatus status, LocalDateTime cutoff);

    /**
     * Contar solicitudes por estado.
     */
    long countByStatus(RequestStatus status);

    /**
     * Contar las solicitudes de un cliente cuyo estado está en la lista dada (p. ej. estados
     * activos/no terminales, para la métrica "solicitudes activas").
     */
    long countByClientIdAndStatusIn(Long clientId, List<RequestStatus> statuses);
}
