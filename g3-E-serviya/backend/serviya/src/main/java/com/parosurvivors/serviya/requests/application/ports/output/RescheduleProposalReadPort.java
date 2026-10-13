package com.parosurvivors.serviya.requests.application.ports.output;

import com.parosurvivors.serviya.requests.application.dto.item.RescheduleProposalItem;
import com.parosurvivors.serviya.requests.application.dto.query.SearchRescheduleProposalsQuery;
import com.parosurvivors.serviya.requests.domain.ProposalStatus;
import com.parosurvivors.serviya.requests.domain.RescheduleProposal;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Puerto de salida de LECTURA (consultas) de propuestas de reprogramacion. Reune TODAS las lecturas:
 * <ul>
 *   <li>Finds de dominio (findBy…) que devuelven {@link RescheduleProposal}, para cargar la propuesta
 *       (los servicios de comando la cargan, mutan y persisten via el puerto de persistencia).</li>
 *   <li>Vistas enriquecidas CQRS-light (Item/Result) que cruzan proposals -> service_requests ->
 *       services -> user_profiles, resueltas con queries nativas.</li>
 * </ul>
 * El puerto de persistencia queda solo con las mutaciones (save/update).
 */
public interface RescheduleProposalReadPort {

    // --- Finds de dominio ---
    Optional<RescheduleProposal> findById(Long id);

    List<RescheduleProposal> findByRequestId(Long requestId);

    List<RescheduleProposal> findByRequestIdAndStatus(Long requestId, ProposalStatus status);

    List<RescheduleProposal> findByStatus(ProposalStatus status);

    /** Mantenimiento por tiempo: propuestas en un estado dado cuya fecha propuesta ya venció. */
    List<RescheduleProposal> findByStatusAndProposedDateBefore(ProposalStatus status, java.time.LocalDateTime cutoff);

    // --- Vistas enriquecidas (read-models) ---
    Page<RescheduleProposalItem> searchReceivedByClient(SearchRescheduleProposalsQuery query, Pageable pageable);

    Page<RescheduleProposalItem> searchSentByOfferer(SearchRescheduleProposalsQuery query, Pageable pageable);
}
