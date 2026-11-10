package com.parosurvivors.chat.conversations.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Canal 1 a 1. Agregado raíz: custodia las invariantes de §7.1 de la guía.
 *
 * <p>No crea mensajes (eso es del feature de mensajes): {@link #registerMessage} valida que se pueda escribir
 * y asigna el siguiente {@code seq}. La unicidad real del seq la garantiza la BD, que bloquea la fila de la
 * conversación (SELECT ... FOR UPDATE) antes de cargar este agregado.
 */
public final class Conversation {

    private final Long id;
    private final ConversationType type;
    private final Long contextId;
    private final List<Participant> participants;
    private final Instant createdAt;
    private ConversationStatus status;
    private String contextStatus;
    private Instant contextStatusAt;
    private long lastSeq;
    private Instant lastMessageAt;

    public Conversation(Long id, ConversationType type, Long contextId, ConversationStatus status,
                        String contextStatus, Instant contextStatusAt, long lastSeq, Instant lastMessageAt,
                        Instant createdAt, List<Participant> participants) {
        this.id = id;
        this.type = type;
        this.contextId = contextId;
        this.status = status;
        this.contextStatus = contextStatus;
        this.contextStatusAt = contextStatusAt;
        this.lastSeq = lastSeq;
        this.lastMessageAt = lastMessageAt;
        this.createdAt = createdAt;
        this.participants = List.copyOf(participants);
    }

    /**
     * Abre la conversación de una solicitud (DP-01). Si la solicitud ya llega en un estado terminal (evento
     * tardío), nace en solo lectura.
     */
    public static Conversation openForRequest(long requestId, long clientId, long offererId, String requestStatus,
                                              Instant occurredAt, TerminalRequestStatuses terminal) {
        if (clientId == offererId) {
            throw ConversationException.invalid("El cliente y el oferente no pueden ser el mismo usuario");
        }
        ConversationStatus initial = terminal.isTerminal(requestStatus)
                ? ConversationStatus.READ_ONLY : ConversationStatus.ACTIVE;
        return new Conversation(null, ConversationType.REQUEST, requestId, initial, requestStatus, occurredAt,
                0, occurredAt, occurredAt,
                List.of(new Participant(clientId, ParticipantRole.CLIENT, 0, occurredAt),
                        new Participant(offererId, ParticipantRole.OFFERER, 0, occurredAt)));
    }

    public boolean isParticipant(long userId) {
        return participant(userId).isPresent();
    }

    public Optional<Participant> participant(long userId) {
        return participants.stream().filter(p -> p.userId() == userId).findFirst();
    }

    /** Los participantes distintos de {@code userId} (en 1 a 1, el otro lado). */
    public List<Participant> othersThan(long userId) {
        return participants.stream().filter(p -> p.userId() != userId).toList();
    }

    public boolean isWritable() {
        return status == ConversationStatus.ACTIVE;
    }

    /** Lectura: solo participantes (la excepción de ADMIN auditado es de la fase 4). */
    public void assertCanRead(long userId) {
        if (!isParticipant(userId)) {
            throw ConversationException.notParticipant();
        }
    }

    /** Escritura: participante y conversación ACTIVE. El orden importa: a un tercero no se le revela el estado. */
    public void assertCanWrite(long userId) {
        assertCanRead(userId);
        if (!isWritable()) {
            throw ConversationException.readOnly();
        }
    }

    /**
     * Reserva el siguiente número de secuencia para un mensaje de {@code senderId} y avanza su lectura hasta él
     * (el emisor no cuenta su propio mensaje como no leído). Mensajes SYSTEM: {@code senderId} null.
     */
    public long registerMessage(Long senderId, Instant at) {
        if (senderId != null) {
            assertCanWrite(senderId);
        } else if (!isWritable()) {
            throw ConversationException.readOnly();
        }
        lastSeq += 1;
        lastMessageAt = at;
        if (senderId != null) {
            participant(senderId).orElseThrow().advanceReadTo(lastSeq);
        }
        return lastSeq;
    }

    /** Marca leído hasta {@code seq} (acotado a lo que existe). Devuelve el seq efectivo o vacío si no avanzó. */
    public Optional<Long> markRead(long userId, long seq) {
        Participant p = participant(userId).orElseThrow(ConversationException::notParticipant);
        long effective = Math.min(seq, lastSeq);
        return p.advanceReadTo(effective) ? Optional.of(effective) : Optional.empty();
    }

    public long unreadFor(long userId) {
        Participant p = participant(userId).orElseThrow(ConversationException::notParticipant);
        return lastSeq - p.lastReadSeq();
    }

    /**
     * Aplica un cambio de estado de la solicitud (ADR-012). Ignora eventos más viejos que el último aplicado
     * (fuera de orden). Un estado terminal pasa la conversación REQUEST a solo lectura, y es definitivo (DP-02).
     *
     * @return true si algo cambió (estado de solicitud o de conversación)
     */
    public boolean applyRequestStatus(String newStatus, Instant occurredAt, TerminalRequestStatuses terminal) {
        if (type != ConversationType.REQUEST) {
            throw ConversationException.invalid("Solo las conversaciones REQUEST siguen el estado de una solicitud");
        }
        if (contextStatusAt != null && occurredAt.isBefore(contextStatusAt)) {
            return false;
        }
        boolean changed = !newStatus.equals(contextStatus);
        contextStatus = newStatus;
        contextStatusAt = occurredAt;
        if (terminal.isTerminal(newStatus) && status == ConversationStatus.ACTIVE) {
            status = ConversationStatus.READ_ONLY;
            changed = true;
        }
        // Nunca se reabre: un estado no terminal posterior no devuelve la conversación a ACTIVE.
        return changed;
    }

    public Long id() { return id; }

    public ConversationType type() { return type; }

    public Long contextId() { return contextId; }

    public ConversationStatus status() { return status; }

    public String contextStatus() { return contextStatus; }

    public Instant contextStatusAt() { return contextStatusAt; }

    public long lastSeq() { return lastSeq; }

    public Instant lastMessageAt() { return lastMessageAt; }

    public Instant createdAt() { return createdAt; }

    public List<Participant> participants() { return participants; }
}
