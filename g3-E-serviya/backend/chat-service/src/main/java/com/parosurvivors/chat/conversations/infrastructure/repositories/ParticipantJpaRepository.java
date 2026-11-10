package com.parosurvivors.chat.conversations.infrastructure.repositories;

import com.parosurvivors.chat.conversations.infrastructure.entities.ParticipantEntity;
import com.parosurvivors.chat.conversations.infrastructure.entities.ParticipantId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface ParticipantJpaRepository extends JpaRepository<ParticipantEntity, ParticipantId> {

    @Query("select p from ParticipantEntity p where p.id.conversationId = :conversationId order by p.id.userId")
    List<ParticipantEntity> findByConversationId(@Param("conversationId") long conversationId);

    /**
     * Avance monótono y atómico: equivale a GREATEST(last_read_seq, :seq). Nunca retrocede, aunque dos escrituras
     * concurrentes (un envío y un «marcar leído») se crucen con valores leídos en momentos distintos.
     */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update ParticipantEntity p set p.lastReadSeq = :seq "
            + "where p.id.conversationId = :conversationId and p.id.userId = :userId and p.lastReadSeq < :seq")
    int advanceReadPointer(@Param("conversationId") long conversationId, @Param("userId") long userId,
                           @Param("seq") long seq);
}
