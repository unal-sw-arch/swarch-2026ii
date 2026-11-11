package com.parosurvivors.chat.conversations.infrastructure.entities;

import com.parosurvivors.chat.conversations.domain.ConversationStatus;
import com.parosurvivors.chat.conversations.domain.ConversationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** La columna generada {@code request_ctx} no se mapea: solo existe para el índice único. */
@Entity
@Table(name = "conversations")
public class ConversationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ConversationType type;

    @Column(name = "context_id")
    private Long contextId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ConversationStatus status;

    @Column(name = "context_status", length = 40)
    private String contextStatus;

    @Column(name = "context_status_at")
    private Instant contextStatusAt;

    @Column(name = "last_seq", nullable = false)
    private long lastSeq;

    @Column(name = "last_message_at", nullable = false)
    private Instant lastMessageAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ConversationEntity() {
    }

    public ConversationEntity(ConversationType type, Long contextId, ConversationStatus status, String contextStatus,
                              Instant contextStatusAt, long lastSeq, Instant lastMessageAt, Instant createdAt) {
        this.type = type;
        this.contextId = contextId;
        this.status = status;
        this.contextStatus = contextStatus;
        this.contextStatusAt = contextStatusAt;
        this.lastSeq = lastSeq;
        this.lastMessageAt = lastMessageAt;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public ConversationType getType() { return type; }
    public Long getContextId() { return contextId; }
    public ConversationStatus getStatus() { return status; }
    public String getContextStatus() { return contextStatus; }
    public Instant getContextStatusAt() { return contextStatusAt; }
    public long getLastSeq() { return lastSeq; }
    public Instant getLastMessageAt() { return lastMessageAt; }
    public Instant getCreatedAt() { return createdAt; }

    public void updateFrom(ConversationStatus status, String contextStatus, Instant contextStatusAt, long lastSeq,
                           Instant lastMessageAt) {
        this.status = status;
        this.contextStatus = contextStatus;
        this.contextStatusAt = contextStatusAt;
        this.lastSeq = lastSeq;
        this.lastMessageAt = lastMessageAt;
    }
}
