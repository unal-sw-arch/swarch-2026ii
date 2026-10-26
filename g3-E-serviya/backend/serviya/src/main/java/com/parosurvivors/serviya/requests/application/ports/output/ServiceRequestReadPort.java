package com.parosurvivors.serviya.requests.application.ports.output;

import com.parosurvivors.serviya.requests.application.dto.item.ServiceRequestSummaryItem;
import com.parosurvivors.serviya.requests.application.dto.query.SearchServiceRequestsQuery;
import com.parosurvivors.serviya.requests.domain.RequestStatus;
import com.parosurvivors.serviya.requests.domain.ServiceRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Puerto de salida de LECTURA (consultas) de solicitudes de servicio. Reune TODAS las lecturas
 * (mismo split que RescheduleProposalReadPort):
 * <ul>
 *   <li>Finds de dominio (findBy…) y conteos que devuelven {@link ServiceRequest}, para cargar la
 *       solicitud (los servicios de comando la cargan, mutan y persisten via el puerto de persistencia).</li>
 *   <li>Agenda (solicitudes futuras) y mantenimiento por tiempo (por estado + fecha).</li>
 *   <li>Vistas enriquecidas CQRS-light (SummaryItem) que cruzan service_requests -> services ->
 *       categories -> user_profiles -> addresses, resueltas con queries nativas.</li>
 * </ul>
 * El puerto de persistencia queda solo con las mutaciones (save/update).
 */
public interface ServiceRequestReadPort {

    // --- Finds de dominio ---
    Optional<ServiceRequest> findById(Long id);

    List<ServiceRequest> findByClientId(Long clientId);

    List<ServiceRequest> findByOffererId(Long offererId);

    List<ServiceRequest> findByServiceId(Long serviceId);

    List<ServiceRequest> findByStatus(RequestStatus status);

    Optional<ServiceRequest> findByPreviousRequestId(Long previousRequestId);

    /** Solicitudes en las que el usuario participa (cliente u oferente) con estado en {@code statuses}. */
    List<ServiceRequest> findByParticipantAndStatusIn(Long userId, List<RequestStatus> statuses);

    /** Cuenta las solicitudes de un cliente cuyo estado está en {@code statuses} (sin cargar entidades). */
    long countByClientIdAndStatusIn(Long clientId, List<RequestStatus> statuses);

    // --- Agenda: solicitudes futuras (no completadas) ---
    Page<ServiceRequest> findClientFutureRequests(Long clientId, List<String> statuses, Pageable pageable);

    Page<ServiceRequest> findOffererFutureRequests(Long offererId, List<String> statuses, Pageable pageable);

    // --- Mantenimiento por tiempo: vencidas por fecha en un estado dado ---
    List<ServiceRequest> findByStatusAndScheduledDateBefore(RequestStatus status, LocalDateTime cutoff);

    List<ServiceRequest> findByStatusAndCompletedAtBefore(RequestStatus status, LocalDateTime cutoff);

    // --- Vistas enriquecidas (read-models) ---
    /** Solicitudes donde el viewer es el cliente; la contraparte mostrada es el oferente. */
    Page<ServiceRequestSummaryItem> searchByClient(SearchServiceRequestsQuery query, Pageable pageable);

    /** Solicitudes donde el viewer es el oferente; la contraparte mostrada es el cliente. */
    Page<ServiceRequestSummaryItem> searchByOfferer(SearchServiceRequestsQuery query, Pageable pageable);
}
