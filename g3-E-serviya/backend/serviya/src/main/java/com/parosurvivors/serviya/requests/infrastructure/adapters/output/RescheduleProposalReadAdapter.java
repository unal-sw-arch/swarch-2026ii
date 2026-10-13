package com.parosurvivors.serviya.requests.infrastructure.adapters.output;

import com.parosurvivors.serviya.requests.application.dto.item.RescheduleProposalItem;
import com.parosurvivors.serviya.requests.application.dto.query.SearchRescheduleProposalsQuery;
import com.parosurvivors.serviya.requests.application.ports.output.RescheduleProposalReadPort;
import com.parosurvivors.serviya.requests.domain.ProposalStatus;
import com.parosurvivors.serviya.requests.domain.RescheduleProposal;
import com.parosurvivors.serviya.requests.infrastructure.mappers.RescheduleProposalPersistenceMapper;
import com.parosurvivors.serviya.requests.infrastructure.repositories.RescheduleProposalRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Component;

/**
 * Adapter de LECTURA (CQRS-light) de propuestas de reprogramacion. Queries nativas dinamicas
 * (proposals -> service_requests -> services -> user_profiles [+ categories/addresses en el detalle]).
 * Los read-models (Item/Result) los construye JPA directamente via @SqlResultSetMapping/@ConstructorResult
 * definidos en RescheduleProposalEntity: el SELECT alias cada columna al nombre esperado por el mapping,
 * asi que no hay mapeo manual de filas.
 *
 * <p>Con client_id/offerer_id denormalizados en reschedule_proposals, el filtro por parte y el orden
 * created_at DESC salen de un indice compuesto sobre p (sin pasar por service_requests ni filesort).
 * Los nombres de columna viewer/contraparte son literales controlados por este codigo (no hay inyeccion).
 */
@Component
@RequiredArgsConstructor
public class RescheduleProposalReadAdapter implements RescheduleProposalReadPort {

    @PersistenceContext
    private EntityManager em;

    private final RescheduleProposalRepository repository;
    private final RescheduleProposalPersistenceMapper mapper;

    // =====================================================
    // FINDS DE DOMINIO (repository + mapper)
    // =====================================================

    @Override
    public Optional<RescheduleProposal> findById(Long id) {
        return repository.findById(id).map(mapper::toDomain);
    }

    @Override
    public List<RescheduleProposal> findByRequestId(Long requestId) {
        return repository.findByRequestId(requestId).stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<RescheduleProposal> findByRequestIdAndStatus(Long requestId, ProposalStatus status) {
        return repository.findByRequestIdAndStatus(requestId, status).stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<RescheduleProposal> findByStatus(ProposalStatus status) {
        return repository.findByStatus(status).stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<RescheduleProposal> findByStatusAndProposedDateBefore(ProposalStatus status, java.time.LocalDateTime cutoff) {
        return repository.findByStatusAndProposedDateBefore(status, cutoff).stream().map(mapper::toDomain).toList();
    }

    // =====================================================
    // VISTAS ENRIQUECIDAS (queries nativas)
    // =====================================================

    @Override
    public Page<RescheduleProposalItem> searchReceivedByClient(SearchRescheduleProposalsQuery query, Pageable pageable) {
        // El cliente ve sus propuestas recibidas; la contraparte es el oferente.
        return search(query, pageable, "client_id", "offerer_id");
    }

    @Override
    public Page<RescheduleProposalItem> searchSentByOfferer(SearchRescheduleProposalsQuery query, Pageable pageable) {
        // El oferente ve sus propuestas enviadas; la contraparte es el cliente.
        return search(query, pageable, "offerer_id", "client_id");
    }

    private Page<RescheduleProposalItem> search(SearchRescheduleProposalsQuery q, Pageable pageable,
                                                String viewerColumn, String counterpartyColumn) {
        Map<String, Object> params = new HashMap<>();
        params.put("viewerId", q.viewerId());
        String filters = buildFilters(q, params);

        // Filtro por parte + ORDER BY created_at resueltos por el indice compuesto (p.<parte>, created_at).
        String where = " WHERE p." + viewerColumn + " = :viewerId" + filters;

        // r hace falta por la fecha original y el titulo del servicio; el enriquecimiento va con LEFT JOIN.
        String dataSql = "SELECT p.id AS proposalId, p.status AS status,"
                + " r.scheduled_date AS originalScheduledDate, p.proposed_date AS proposedDate,"
                + " s.title AS serviceTitle, up.full_name AS counterpartyName,"
                + " up.profile_photo_url AS counterpartyPhotoUrl, p.created_at AS createdAt"
                + " FROM reschedule_proposals p"
                + " JOIN service_requests r ON r.id = p.request_id"
                + " LEFT JOIN services s ON s.id = r.service_id"
                + " LEFT JOIN user_profiles up ON up.user_id = p." + counterpartyColumn
                + where
                + " ORDER BY p.created_at DESC";
        Query dataQuery = em.createNativeQuery(dataSql, "RescheduleProposalItemMapping");
        params.forEach(dataQuery::setParameter);
        dataQuery.setFirstResult((int) pageable.getOffset());
        dataQuery.setMaxResults(pageable.getPageSize());

        @SuppressWarnings("unchecked")
        List<RescheduleProposalItem> items = dataQuery.getResultList();

        // El COUNT vive solo sobre p; solo si se filtra por serviceId (columna de r) hace falta el join.
        // PageableExecutionUtils ademas omite el COUNT cuando el total se deduce del contenido.
        boolean needsRequestJoin = q.serviceId() != null;
        String countFrom = " FROM reschedule_proposals p"
                + (needsRequestJoin ? " JOIN service_requests r ON r.id = p.request_id" : "");
        return PageableExecutionUtils.getPage(items, pageable, () -> {
            Query countQuery = em.createNativeQuery("SELECT COUNT(*)" + countFrom + where);
            params.forEach(countQuery::setParameter);
            return ((Number) countQuery.getSingleResult()).longValue();
        });
    }

    /** Anexa una condicion AND por cada filtro presente y registra su parametro. */
    private String buildFilters(SearchRescheduleProposalsQuery q, Map<String, Object> params) {
        StringBuilder sb = new StringBuilder();
        if (q.statuses() != null && !q.statuses().isEmpty()) {
            sb.append(" AND p.status IN (:statuses)");
            params.put("statuses", q.statuses());
        }
        if (q.proposedFrom() != null) {
            sb.append(" AND p.proposed_date >= :proposedFrom");
            params.put("proposedFrom", q.proposedFrom());
        }
        if (q.proposedTo() != null) {
            sb.append(" AND p.proposed_date <= :proposedTo");
            params.put("proposedTo", q.proposedTo());
        }
        if (q.createdFrom() != null) {
            sb.append(" AND p.created_at >= :createdFrom");
            params.put("createdFrom", q.createdFrom());
        }
        if (q.createdTo() != null) {
            sb.append(" AND p.created_at <= :createdTo");
            params.put("createdTo", q.createdTo());
        }
        if (q.serviceId() != null) {
            sb.append(" AND r.service_id = :serviceId");
            params.put("serviceId", q.serviceId());
        }
        return sb.toString();
    }
}
