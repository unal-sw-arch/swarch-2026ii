package com.parosurvivors.chat.messaging.application.ports.output;

import com.parosurvivors.chat.messaging.domain.Message;
import java.util.List;
import java.util.Optional;

public interface MessageRepository {

    Message save(Message message);

    Optional<Message> findByClientMessageId(long conversationId, long senderId, String clientMessageId);

    /**
     * Página de historial SIEMPRE en orden ascendente por seq.
     * <ul>
     *   <li>{@code afterSeq != null}: los primeros {@code limit} con {@code seq > afterSeq} (sincronización).</li>
     *   <li>si no: los {@code limit} más recientes con {@code seq < beforeSeq} (o los últimos si es nulo).</li>
     * </ul>
     */
    List<Message> findPage(long conversationId, Long beforeSeq, Long afterSeq, int limit);
}
