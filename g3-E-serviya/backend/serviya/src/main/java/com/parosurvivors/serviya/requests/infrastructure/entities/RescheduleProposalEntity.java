package com.parosurvivors.serviya.requests.infrastructure.entities;

import com.parosurvivors.serviya.requests.application.dto.item.RescheduleProposalItem;
import com.parosurvivors.serviya.requests.domain.ProposalStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * El {@code @SqlResultSetMapping} construye el read-model de listado directamente desde las columnas
 * de la query nativa de {@code RescheduleProposalReadAdapter} (ConstructorResult -> constructor
 * canonico del record; no hay mapeo posterior). El {@code name} de cada columna casa con el alias del
 * SELECT; el ORDEN debe coincidir con los componentes del record. El detalle NO usa mapping: se
 * compone por puertos en {@code RescheduleProposalService.getProposalDetail}.
 */
@Entity
@Table(name = "reschedule_proposals")
@SqlResultSetMapping(
    name = "RescheduleProposalItemMapping",
    classes = @ConstructorResult(
        targetClass = RescheduleProposalItem.class,
        columns = {
            @ColumnResult(name = "proposalId", type = Long.class),
            @ColumnResult(name = "status", type = String.class),
            @ColumnResult(name = "originalScheduledDate", type = LocalDateTime.class),
            @ColumnResult(name = "proposedDate", type = LocalDateTime.class),
            @ColumnResult(name = "serviceTitle", type = String.class),
            @ColumnResult(name = "counterpartyName", type = String.class),
            @ColumnResult(name = "counterpartyPhotoUrl", type = String.class),
            @ColumnResult(name = "createdAt", type = LocalDateTime.class)
        }))
@Getter
@Setter
public class RescheduleProposalEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", nullable = false)
    private Long requestId;

    @Column(name = "client_id", nullable = false)
    private Long clientId;

    @Column(name = "offerer_id", nullable = false)
    private Long offererId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Column(name = "proposed_date", nullable = false)
    private LocalDateTime proposedDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProposalStatus status;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "responded_at")
    private LocalDateTime respondedAt;

    @Version
    @Column(name = "version")
    private Long version;
}
