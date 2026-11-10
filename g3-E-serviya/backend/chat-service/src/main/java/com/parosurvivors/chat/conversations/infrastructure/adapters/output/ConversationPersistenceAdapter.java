package com.parosurvivors.chat.conversations.infrastructure.adapters.output;

import com.parosurvivors.chat.conversations.application.exceptions.ConversationAlreadyExistsException;
import com.parosurvivors.chat.conversations.application.ports.output.ConversationRepository;
import com.parosurvivors.chat.conversations.domain.Conversation;
import com.parosurvivors.chat.conversations.domain.ConversationType;
import com.parosurvivors.chat.conversations.domain.Participant;
import com.parosurvivors.chat.conversations.infrastructure.entities.ConversationEntity;
import com.parosurvivors.chat.conversations.infrastructure.entities.ParticipantEntity;
import com.parosurvivors.chat.conversations.infrastructure.entities.ParticipantId;
import com.parosurvivors.chat.conversations.infrastructure.repositories.ConversationJpaRepository;
import com.parosurvivors.chat.conversations.infrastructure.repositories.ParticipantJpaRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class ConversationPersistenceAdapter implements ConversationRepository {

    private final ConversationJpaRepository conversations;
    private final ParticipantJpaRepository participants;

    ConversationPersistenceAdapter(ConversationJpaRepository conversations, ParticipantJpaRepository participants) {
        this.conversations = conversations;
        this.participants = participants;
    }

    @Override
    @Transactional
    public Conversation save(Conversation c) {
        if (c.id() == null) {
            return insert(c);
        }
        ConversationEntity entity = conversations.findById(c.id()).orElseThrow();
        entity.updateFrom(c.status(), c.contextStatus(), c.contextStatusAt(), c.lastSeq(), c.lastMessageAt());
        conversations.save(entity);
        // Solo hacia adelante: un participante con un valor leído antes de un «marcar leído» concurrente no lo pisa.
        for (Participant p : c.participants()) {
            participants.advanceReadPointer(entity.getId(), p.userId(), p.lastReadSeq());
        }
        return toDomain(entity);
    }

    private Conversation insert(Conversation c) {
        ConversationEntity entity = new ConversationEntity(c.type(), c.contextId(), c.status(), c.contextStatus(),
                c.contextStatusAt(), c.lastSeq(), c.lastMessageAt(), c.createdAt());
        try {
            entity = conversations.saveAndFlush(entity);
        } catch (DataIntegrityViolationException e) {
            if (c.type() == ConversationType.REQUEST) {
                throw new ConversationAlreadyExistsException(c.contextId());
            }
            throw e;
        }
        for (Participant p : c.participants()) {
            participants.save(new ParticipantEntity(new ParticipantId(entity.getId(), p.userId()), p.role(),
                    p.lastReadSeq(), p.joinedAt()));
        }
        return toDomain(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Conversation> findById(long id) {
        return conversations.findById(id).map(this::toDomain);
    }

    @Override
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public Optional<Conversation> findByIdForUpdate(long id) {
        return conversations.findByIdForUpdate(id).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Conversation> findByRequestId(long requestId) {
        return conversations.findByRequestId(requestId).map(this::toDomain);
    }

    @Override
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public Optional<Conversation> findByRequestIdForUpdate(long requestId) {
        return conversations.findByRequestIdForUpdate(requestId).map(this::toDomain);
    }

    @Override
    public void advanceReadPointer(long conversationId, long userId, long seq) {
        participants.advanceReadPointer(conversationId, userId, seq);
    }

    private Conversation toDomain(ConversationEntity e) {
        List<Participant> ps = participants.findByConversationId(e.getId()).stream()
                .map(p -> new Participant(p.getId().userId(), p.getRole(), p.getLastReadSeq(), p.getJoinedAt()))
                .toList();
        return new Conversation(e.getId(), e.getType(), e.getContextId(), e.getStatus(), e.getContextStatus(),
                e.getContextStatusAt(), e.getLastSeq(), e.getLastMessageAt(), e.getCreatedAt(), ps);
    }
}
