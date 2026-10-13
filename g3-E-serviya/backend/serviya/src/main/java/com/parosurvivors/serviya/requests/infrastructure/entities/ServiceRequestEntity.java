package com.parosurvivors.serviya.requests.infrastructure.entities;

import com.parosurvivors.serviya.requests.application.dto.item.ServiceRequestSummaryItem;
import com.parosurvivors.serviya.requests.domain.RequestStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * El {@code @SqlResultSetMapping} construye el read-model de listado directamente desde las columnas
 * de la query nativa de {@code ServiceRequestReadAdapter} (ConstructorResult -> constructor canonico
 * del record; sin mapeo posterior). El {@code name} de cada columna casa con el alias del SELECT y el
 * ORDEN debe coincidir con los componentes del record. El detalle NO usa mapping: se compone en el servicio.
 */
@Entity
@Table(name = "service_requests")
@SqlResultSetMapping(
    name = "ServiceRequestSummaryMapping",
    classes = @ConstructorResult(
        targetClass = ServiceRequestSummaryItem.class,
        columns = {
            @ColumnResult(name = "requestId", type = Long.class),
            @ColumnResult(name = "status", type = String.class),
            @ColumnResult(name = "scheduledDate", type = LocalDateTime.class),
            @ColumnResult(name = "requestedPrice", type = BigDecimal.class),
            @ColumnResult(name = "previousRequestId", type = Long.class),
            @ColumnResult(name = "createdAt", type = LocalDateTime.class),
            @ColumnResult(name = "serviceId", type = Long.class),
            @ColumnResult(name = "serviceTitle", type = String.class),
            @ColumnResult(name = "categoryName", type = String.class),
            @ColumnResult(name = "counterpartyId", type = Long.class),
            @ColumnResult(name = "counterpartyName", type = String.class),
            @ColumnResult(name = "counterpartyPhotoUrl", type = String.class),
            @ColumnResult(name = "city", type = String.class)
        }))
@Getter
@Setter
public class ServiceRequestEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Integer version;

    @Column(name = "service_id", nullable = false)
    private Long serviceId;

    @Column(name = "previous_request_id", unique = true)
    private Long previousRequestId;

    @Column(name = "client_id", nullable = false)
    private Long clientId;

    @Column(name = "offerer_id", nullable = false)
    private Long offererId;

    @Column(name = "address_id", nullable = false)
    private Long addressId;

    @Column(name = "scheduled_date", nullable = false)
    private LocalDateTime scheduledDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RequestStatus status;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "requested_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal requestedPrice;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "updated_status_at")
    private LocalDateTime updatedStatusAt;
}
