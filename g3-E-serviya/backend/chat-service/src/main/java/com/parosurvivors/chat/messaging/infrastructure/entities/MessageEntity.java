package com.parosurvivors.chat.messaging.infrastructure.entities;

import com.parosurvivors.chat.messaging.domain.MessageKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Inmutable: no hay setters ni actualizaciones (invariante 5). */
@Entity
@Table(name = "messages")
public class MessageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conversation_id", nullable = false, updatable = false)
    private long conversationId;

    @Column(nullable = false, updatable = false)
    private long seq;

    @Column(name = "sender_id", updatable = false)
    private Long senderId;

    @Column(name = "client_message_id", length = 36, columnDefinition = "CHAR(36)", updatable = false)
    private String clientMessageId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private MessageKind kind;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "JSON", updatable = false)
    private List<String> flags;

    @Column(nullable = false, columnDefinition = "TEXT", updatable = false)
    private String body;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected MessageEntity() {
    }

    public MessageEntity(long conversationId, long seq, Long senderId, String clientMessageId, MessageKind kind,
                         List<String> flags, String body, Instant createdAt) {
        this.conversationId = conversationId;
        this.seq = seq;
        this.senderId = senderId;
        this.clientMessageId = clientMessageId;
        this.kind = kind;
        this.flags = flags;
        this.body = body;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public long getConversationId() { return conversationId; }
    public long getSeq() { return seq; }
    public Long getSenderId() { return senderId; }
    public String getClientMessageId() { return clientMessageId; }
    public MessageKind getKind() { return kind; }
    public List<String> getFlags() { return flags; }
    public String getBody() { return body; }
    public Instant getCreatedAt() { return createdAt; }
}
