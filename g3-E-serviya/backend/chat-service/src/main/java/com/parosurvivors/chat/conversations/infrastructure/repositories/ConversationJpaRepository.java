package com.parosurvivors.chat.conversations.infrastructure.repositories;

import com.parosurvivors.chat.conversations.infrastructure.entities.ConversationEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConversationJpaRepository extends JpaRepository<ConversationEntity, Long> {

    /** SELECT ... FOR UPDATE: serializa los envíos de esta conversación (§7.3). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ConversationEntity c where c.id = :id")
    Optional<ConversationEntity> findByIdForUpdate(@Param("id") long id);

    @Query("select c from ConversationEntity c where c.type = com.parosurvivors.chat.conversations.domain.ConversationType.REQUEST and c.contextId = :requestId")
    Optional<ConversationEntity> findByRequestId(@Param("requestId") long requestId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ConversationEntity c where c.type = com.parosurvivors.chat.conversations.domain.ConversationType.REQUEST and c.contextId = :requestId")
    Optional<ConversationEntity> findByRequestIdForUpdate(@Param("requestId") long requestId);
}
