package com.parosurvivors.chat.messaging.infrastructure.repositories;

import com.parosurvivors.chat.messaging.infrastructure.entities.MessageEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageJpaRepository extends JpaRepository<MessageEntity, Long> {

    Optional<MessageEntity> findByConversationIdAndSenderIdAndClientMessageId(long conversationId, long senderId,
                                                                              String clientMessageId);

    /** Los más recientes primero (se invierte al devolver). */
    List<MessageEntity> findByConversationIdOrderBySeqDesc(long conversationId, Pageable page);

    List<MessageEntity> findByConversationIdAndSeqLessThanOrderBySeqDesc(long conversationId, long seq, Pageable page);

    List<MessageEntity> findByConversationIdAndSeqGreaterThanOrderBySeqAsc(long conversationId, long seq, Pageable page);
}
