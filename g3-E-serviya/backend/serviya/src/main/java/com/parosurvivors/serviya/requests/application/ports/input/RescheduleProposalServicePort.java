package com.parosurvivors.serviya.requests.application.ports.input;

import com.parosurvivors.serviya.requests.application.dto.command.CreateRescheduleProposalCommand;
import com.parosurvivors.serviya.requests.application.dto.item.RescheduleProposalItem;
import com.parosurvivors.serviya.requests.application.dto.query.SearchRescheduleProposalsQuery;
import com.parosurvivors.serviya.requests.application.dto.result.RescheduleProposalDetailResult;
import com.parosurvivors.serviya.requests.domain.RescheduleProposal;
import com.parosurvivors.serviya.requests.domain.ServiceRequest;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Puerto de entrada de RescheduleProposalService. createProposal recibe Command; las listas reciben
 * Query + Pageable y devuelven read-models (Item/Result). getProposalsByRequest y las transiciones
 * devuelven dominio. Nunca tipos web. Ver documents/project-structure/estructura-servicios.docx (módulo 4).
 */
public interface RescheduleProposalServicePort {

    RescheduleProposal createProposal(CreateRescheduleProposalCommand command);

    ServiceRequest acceptProposal(Long proposalId, Long clientId);

    void rejectProposal(Long proposalId, Long clientId);

    void cancelProposal(Long proposalId, Long offererId);

    /** Propuestas recibidas por el cliente (paginadas, created_at DESC, filtros del Query). */
    Page<RescheduleProposalItem> getProposalsForClient(SearchRescheduleProposalsQuery query, Pageable pageable);

    /** Propuestas enviadas por el oferente (paginadas, created_at DESC, filtros del Query). */
    Page<RescheduleProposalItem> getProposalsByOfferer(SearchRescheduleProposalsQuery query, Pageable pageable);

    /** Detalle enriquecido de una propuesta; la otra parte es relativa a quien consulta (viewerId del JWT). */
    RescheduleProposalDetailResult getProposalDetail(Long proposalId, Long viewerId);

    /**
     * Historial de propuestas de una solicitud (crudo, para la pagina de la solicitud). Solo
     * accesible para las partes (cliente u oferente) de la solicitud (viewerId del JWT).
     */
    List<RescheduleProposal> getProposalsByRequest(Long requestId, Long viewerId);

    /**
     * Resuelve (a un estado terminal) las propuestas PENDING de una solicitud cuando ésta cambia
     * de estado. Lógica centralizada que invocan los orquestadores de {@code ServiceRequest};
     * devuelven cuántas propuestas resolvieron.
     */
    int supersedePendingProposals(Long requestId); // reprogramación libre → SUPERSEDED

    int cancelPendingProposals(Long requestId);     // cancelar / completar / presuntamente-completar → CANCELLED
}
