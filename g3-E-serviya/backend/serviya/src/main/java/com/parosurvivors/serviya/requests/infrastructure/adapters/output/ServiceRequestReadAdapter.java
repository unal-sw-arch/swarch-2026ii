package com.parosurvivors.serviya.requests.infrastructure.adapters.output;

import com.parosurvivors.serviya.requests.application.dto.item.ServiceRequestSummaryItem;
import com.parosurvivors.serviya.requests.application.dto.query.SearchServiceRequestsQuery;
import com.parosurvivors.serviya.requests.application.ports.output.ServiceRequestReadPort;
import com.parosurvivors.serviya.requests.domain.RequestStatus;
import com.parosurvivors.serviya.requests.domain.ServiceRequest;
import com.parosurvivors.serviya.requests.infrastructure.mappers.ServiceRequestPersistenceMapper;
import com.parosurvivors.serviya.requests.infrastructure.repositories.ServiceRequestRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Component;

/**
 * Adapter de LECTURA (CQRS-light) de solicitudes de servicio. Reune todas las lecturas del agregado:
 * finds de dominio + conteos (repository + mapper), agenda/mantenimiento por fecha, y las vistas
 * enriquecidas paginadas (query nativa + @SqlResultSetMapping definido en {@code ServiceRequestEntity}).
 * El puerto de persistencia queda solo con save/update.
 *
 * <p>En la busqueda enriquecida el viewer (cliente u oferente) y la contraparte se resuelven por columna:
 * en la lista del cliente el viewer es {@code client_id} y la contraparte {@code offerer_id}, y a la
 * inversa en la del oferente. Los nombres de columna viewer/contraparte y de orden son literales
 * controlados por este codigo (whitelist), no hay inyeccion.
 */
@Component
@RequiredArgsConstructor
public class ServiceRequestReadAdapter implements ServiceRequestReadPort {

    @PersistenceContext
    private EntityManager em;

    private final ServiceRequestRepository repository;
    private final ServiceRequestPersistenceMapper mapper;

    // =====================================================
    // FINDS DE DOMINIO (repository + mapper)
    // =====================================================

    @Override
    public Optional<ServiceRequest> findById(Long id) {
        return repository.findById(id).map(mapper::toDomain);
    }

    @Override
    public List<ServiceRequest> findByClientId(Long clientId) {
        return repository.findByClientId(clientId).stream().map(mapper::toDomain).collect(Collectors.toList());
    }

    @Override
    public long countByClientIdAndStatusIn(Long clientId, List<RequestStatus> statuses) {
        return repository.countByClientIdAndStatusIn(clientId, statuses);
    }

    @Override
    public List<ServiceRequest> findByOffererId(Long offererId) {
        return repository.findByOffererId(offererId).stream().map(mapper::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<ServiceRequest> findByServiceId(Long serviceId) {
        return repository.findByServiceId(serviceId).stream().map(mapper::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<ServiceRequest> findByStatus(RequestStatus status) {
        return repository.findByStatus(status).stream().map(mapper::toDomain).collect(Collectors.toList());
    }

    @Override
    public Optional<ServiceRequest> findByPreviousRequestId(Long previousRequestId) {
        return repository.findByPreviousRequestId(previousRequestId).map(mapper::toDomain);
    }

    @Override
    public List<ServiceRequest> findByParticipantAndStatusIn(Long userId, List<RequestStatus> statuses) {
        return repository.findByParticipantAndStatusIn(userId, statuses).stream()
                .map(mapper::toDomain).collect(Collectors.toList());
    }

    // =====================================================
    // AGENDA (solicitudes futuras) + MANTENIMIENTO (por fecha)
    // =====================================================

    @Override
    public Page<ServiceRequest> findClientFutureRequests(Long clientId, List<String> statuses, Pageable pageable) {
        return repository.findByClientIdAndScheduledDateAfter(clientId, LocalDateTime.now()).stream()
                .filter(entity -> statuses.contains(entity.getStatus().name()))
                .map(mapper::toDomain)
                .collect(Collectors.collectingAndThen(Collectors.toList(), list -> new PageImpl<>(list, pageable, list.size())));
    }

    @Override
    public Page<ServiceRequest> findOffererFutureRequests(Long offererId, List<String> statuses, Pageable pageable) {
        return repository.findByOffererIdAndScheduledDateAfter(offererId, LocalDateTime.now()).stream()
                .filter(entity -> statuses.contains(entity.getStatus().name()))
                .map(mapper::toDomain)
                .collect(Collectors.collectingAndThen(Collectors.toList(), list -> new PageImpl<>(list, pageable, list.size())));
    }

    @Override
    public List<ServiceRequest> findByStatusAndScheduledDateBefore(RequestStatus status, LocalDateTime cutoff) {
        return repository.findByStatusAndScheduledDateBefore(status, cutoff).stream()
                .map(mapper::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<ServiceRequest> findByStatusAndCompletedAtBefore(RequestStatus status, LocalDateTime cutoff) {
        return repository.findByStatusAndCompletedAtBefore(status, cutoff).stream()
                .map(mapper::toDomain).collect(Collectors.toList());
    }

    // =====================================================
    // VISTAS ENRIQUECIDAS (queries nativas)
    // =====================================================

    @Override
    public Page<ServiceRequestSummaryItem> searchByClient(SearchServiceRequestsQuery query, Pageable pageable) {
        return search(query, pageable, "client_id", "offerer_id");
    }

    @Override
    public Page<ServiceRequestSummaryItem> searchByOfferer(SearchServiceRequestsQuery query, Pageable pageable) {
        return search(query, pageable, "offerer_id", "client_id");
    }

    private Page<ServiceRequestSummaryItem> search(SearchServiceRequestsQuery q, Pageable pageable,
                                                   String viewerColumn, String counterpartyColumn) {
        Map<String, Object> params = new HashMap<>();
        params.put("viewerId", q.viewerId());
        String filters = buildFilters(q, params, counterpartyColumn);
        String where = " WHERE r." + viewerColumn + " = :viewerId" + filters;

        String dataSql = "SELECT r.id AS requestId, r.status AS status, r.scheduled_date AS scheduledDate,"
                + " r.requested_price AS requestedPrice, r.previous_request_id AS previousRequestId,"
                + " r.created_at AS createdAt, r.service_id AS serviceId, s.title AS serviceTitle,"
                + " c.name AS categoryName, r." + counterpartyColumn + " AS counterpartyId,"
                + " up.full_name AS counterpartyName, up.profile_photo_url AS counterpartyPhotoUrl,"
                + " a.city AS city"
                + " FROM service_requests r"
                + " LEFT JOIN services s ON s.id = r.service_id"
                + " LEFT JOIN categories c ON c.id = s.category_id"
                + " LEFT JOIN user_profiles up ON up.user_id = r." + counterpartyColumn
                + " LEFT JOIN addresses a ON a.id = r.address_id"
                + where
                + " ORDER BY " + resolveOrderBy(pageable.getSort(), q);
        Query dataQuery = em.createNativeQuery(dataSql, "ServiceRequestSummaryMapping");
        params.forEach(dataQuery::setParameter);
        dataQuery.setFirstResult((int) pageable.getOffset());
        dataQuery.setMaxResults(pageable.getPageSize());

        @SuppressWarnings("unchecked")
        List<ServiceRequestSummaryItem> items = dataQuery.getResultList();

        // El COUNT solo necesita el join a services si se filtra por columnas de s (categoria o titulo).
        boolean needsServiceJoin = q.categoryId() != null || q.titleQuery() != null;
        String countFrom = " FROM service_requests r"
                + (needsServiceJoin ? " LEFT JOIN services s ON s.id = r.service_id" : "");
        return PageableExecutionUtils.getPage(items, pageable, () -> {
            Query countQuery = em.createNativeQuery("SELECT COUNT(*)" + countFrom + where);
            params.forEach(countQuery::setParameter);
            return ((Number) countQuery.getSingleResult()).longValue();
        });
    }

    /** Anexa una condicion AND por cada filtro presente y registra su parametro. */
    private String buildFilters(SearchServiceRequestsQuery q, Map<String, Object> params, String counterpartyColumn) {
        StringBuilder sb = new StringBuilder();
        if (q.statuses() != null && !q.statuses().isEmpty()) {
            sb.append(" AND r.status IN (:statuses)");
            params.put("statuses", q.statuses());
        }
        if (q.serviceId() != null) {
            sb.append(" AND r.service_id = :serviceId");
            params.put("serviceId", q.serviceId());
        }
        if (q.categoryId() != null) {
            sb.append(" AND s.category_id = :categoryId");
            params.put("categoryId", q.categoryId());
        }
        if (q.counterpartyId() != null) {
            // La contraparte es la otra parte relativa al viewer (columna controlada, no inyeccion).
            sb.append(" AND r.").append(counterpartyColumn).append(" = :counterpartyId");
            params.put("counterpartyId", q.counterpartyId());
        }
        if (q.titleQuery() != null && !q.titleQuery().isBlank()) {
            sb.append(" AND s.title LIKE :titleQuery");
            params.put("titleQuery", "%" + q.titleQuery().strip() + "%");
        }
        if (q.scheduledFrom() != null) {
            sb.append(" AND r.scheduled_date >= :scheduledFrom");
            params.put("scheduledFrom", q.scheduledFrom());
        }
        if (q.scheduledTo() != null) {
            sb.append(" AND r.scheduled_date <= :scheduledTo");
            params.put("scheduledTo", q.scheduledTo());
        }
        if (q.createdFrom() != null) {
            sb.append(" AND r.created_at >= :createdFrom");
            params.put("createdFrom", q.createdFrom());
        }
        if (q.createdTo() != null) {
            sb.append(" AND r.created_at <= :createdTo");
            params.put("createdTo", q.createdTo());
        }
        return sb.toString();
    }

    /**
     * Resuelve el ORDER BY desde el {@link Sort} del Pageable con whitelist de propiedades para evitar
     * inyeccion. Solo se admiten {@code scheduledDate} y {@code createdAt}; por defecto:
     * <ul>
     *   <li>Si el filtro es solo PENDING → {@code r.created_at ASC} (las más antiguas primero)</li>
     *   <li>En cualquier otro caso → {@code r.created_at DESC} (las más recientes primero)</li>
     * </ul>
     */
    private String resolveOrderBy(Sort sort, SearchServiceRequestsQuery query) {
        for (Sort.Order order : sort) {
            String column = switch (order.getProperty()) {
                case "scheduledDate" -> "r.scheduled_date";
                case "createdAt" -> "r.created_at";
                default -> null;
            };
            if (column != null) {
                return column + (order.isAscending() ? " ASC" : " DESC");
            }
        }
        if (isOnlyPendingFilter(query)) {
            return "r.created_at ASC";
        }
        return "r.created_at DESC";
    }

    /** Retorna {@code true} si el único filtro de estado es PENDING (tab Pendientes). */
    private boolean isOnlyPendingFilter(SearchServiceRequestsQuery query) {
        return query.statuses() != null
                && query.statuses().size() == 1
                && "PENDING".equalsIgnoreCase(query.statuses().get(0));
    }
}
