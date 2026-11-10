package com.parosurvivors.chat.conversations.domain;

import java.time.Instant;

/** Usuario dentro de una conversación, con su rol en ella y hasta qué mensaje leyó. */
public final class Participant {

    private final long userId;
    private final ParticipantRole role;
    private final Instant joinedAt;
    private long lastReadSeq;

    public Participant(long userId, ParticipantRole role, long lastReadSeq, Instant joinedAt) {
        this.userId = userId;
        this.role = role;
        this.lastReadSeq = lastReadSeq;
        this.joinedAt = joinedAt;
    }

    public long userId() { return userId; }

    public ParticipantRole role() { return role; }

    public long lastReadSeq() { return lastReadSeq; }

    public Instant joinedAt() { return joinedAt; }

    /** Avanza el puntero de lectura; nunca retrocede. Devuelve true si cambió. */
    boolean advanceReadTo(long seq) {
        if (seq <= lastReadSeq) {
            return false;
        }
        lastReadSeq = seq;
        return true;
    }
}
