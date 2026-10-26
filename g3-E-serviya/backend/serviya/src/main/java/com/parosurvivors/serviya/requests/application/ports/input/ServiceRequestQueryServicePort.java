package com.parosurvivors.serviya.requests.application.ports.input;

import com.parosurvivors.serviya.requests.application.dto.item.RequestHistoryItem;
import com.parosurvivors.serviya.requests.application.dto.item.ServiceRequestSummaryItem;
import com.parosurvivors.serviya.requests.application.dto.query.SearchServiceRequestsQuery;
import com.parosurvivors.serviya.requests.application.dto.result.AdminRequestDetailResult;
import com.parosurvivors.serviya.requests.application.dto.result.ServiceRequestDetailResult;
import com.parosurvivors.serviya.requests.domain.ServiceRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Puerto de entrada de ServiceRequestQueryService (lecturas — CQRS). Las listas devuelven un
 * resumen enriquecido (SummaryItem: servicio + contraparte); los detalles agregados devuelven Result;
 * el historial devuelve Item. La agenda sigue devolviendo dominio (ServiceRequest). Nunca tipos web.
 * Ver documents/project-structure/estructura-servicios.docx (módulo 4).
 */
public interface ServiceRequestQueryServicePort {

    Page<ServiceRequestSummaryItem> getClientRequests(SearchServiceRequestsQuery query, Pageable pageable);

    /** Cuenta las solicitudes "activas" (estados no terminales) de un cliente. Para la métrica de solicitudes activas. */
    long countActiveClientRequests(Long clientId);

    Page<ServiceRequestSummaryItem> getOffererRequests(SearchServiceRequestsQuery query, Pageable pageable);

    //Next two methods area meant for the Agenda feature. Will return future requests, and not completed.
    Page<ServiceRequest> getClientFutureRequests(Long clientId, Pageable pageable);

    Page<ServiceRequest> getOffererFutureRequests(Long offererId, Pageable pageable);

    ServiceRequestDetailResult getRequestDetailForParty(Long requestId, Long requesterId);

    /** Vista administrativa (ambas partes). El gate de rol ADMIN se aplica en el endpoint (Spring Security), no aqui. */
    AdminRequestDetailResult getRequestDetailForAdmin(Long requestId);

    /** Solo accesible para el cliente/oferente participante o un admin (isAdmin hace bypass del check). */
    List<RequestHistoryItem> getRequestHistory(Long requestId, Long requesterId, boolean isAdmin);
}
