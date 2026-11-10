package com.parosurvivors.chat.conversations.infrastructure.entities;

import com.parosurvivors.chat.conversations.domain.ParticipantRole;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "conversation_participants")
public class ParticipantEntity {

    @EmbeddedId
    private ParticipantId id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ParticipantRole role;

    @Column(name = "last_read_seq", nullable = false)
    private long lastReadSeq;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    protected ParticipantEntity() {
    }

    public ParticipantEntity(ParticipantId id, ParticipantRole role, long lastReadSeq, Instant joinedAt) {
        this.id = id;
        this.role = role;
        this.lastReadSeq = lastReadSeq;
        this.joinedAt = joinedAt;
    }

    public ParticipantId getId() { return id; }
    public ParticipantRole getRole() { return role; }
    public long getLastReadSeq() { return lastReadSeq; }
    public Instant getJoinedAt() { return joinedAt; }
}
