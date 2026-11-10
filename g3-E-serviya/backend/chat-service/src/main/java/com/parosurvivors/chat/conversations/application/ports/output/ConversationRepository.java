package com.parosurvivors.chat.conversations.application.ports.output;

import com.parosurvivors.chat.conversations.domain.Conversation;
import java.util.Optional;

public interface ConversationRepository {

    /**
     * Inserta (id nulo) o actualiza. Al insertar una conversación REQUEST cuya solicitud ya tiene una, lanza
     * {@code ConversationAlreadyExistsException}.
     */
    Conversation save(Conversation conversation);

    Optional<Conversation> findById(long id);

    /**
     * Carga bloqueando la fila (SELECT ... FOR UPDATE): serializa los envíos de ESA conversación y es lo que
     * garantiza un {@code seq} sin huecos (§7.3). Solo tiene sentido dentro de una unidad de trabajo.
     */
    Optional<Conversation> findByIdForUpdate(long id);

    Optional<Conversation> findByRequestId(long requestId);

    Optional<Conversation> findByRequestIdForUpdate(long requestId);

    /** Avanza el puntero de lectura sin retroceder jamás (equivale a {@code GREATEST(last_read_seq, :seq)}). */
    void advanceReadPointer(long conversationId, long userId, long seq);
}
