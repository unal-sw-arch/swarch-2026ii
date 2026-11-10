package com.parosurvivors.chat.messaging.infrastructure.adapters.output;

import com.parosurvivors.chat.messaging.application.ports.output.MessageRepository;
import com.parosurvivors.chat.messaging.domain.Message;
import com.parosurvivors.chat.messaging.domain.MessageFlag;
import com.parosurvivors.chat.messaging.infrastructure.entities.MessageEntity;
import com.parosurvivors.chat.messaging.infrastructure.repositories.MessageJpaRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class MessagePersistenceAdapter implements MessageRepository {

    private final MessageJpaRepository repository;

    MessagePersistenceAdapter(MessageJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public Message save(Message m) {
        List<String> flags = m.flags().isEmpty() ? null : m.flags().stream().map(MessageFlag::name).sorted().toList();
        return toDomain(repository.saveAndFlush(new MessageEntity(m.conversationId(), m.seq(), m.senderId(),
                m.clientMessageId(), m.kind(), flags, m.body(), m.createdAt())));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Message> findByClientMessageId(long conversationId, long senderId, String clientMessageId) {
        return repository.findByConversationIdAndSenderIdAndClientMessageId(conversationId, senderId, clientMessageId)
                .map(MessagePersistenceAdapter::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Message> findPage(long conversationId, Long beforeSeq, Long afterSeq, int limit) {
        PageRequest page = PageRequest.of(0, limit);
        if (afterSeq != null) {
            return repository.findByConversationIdAndSeqGreaterThanOrderBySeqAsc(conversationId, afterSeq, page)
                    .stream().map(MessagePersistenceAdapter::toDomain).toList();
        }
        List<MessageEntity> newestFirst = beforeSeq == null
                ? repository.findByConversationIdOrderBySeqDesc(conversationId, page)
                : repository.findByConversationIdAndSeqLessThanOrderBySeqDesc(conversationId, beforeSeq, page);
        List<Message> ascending = new ArrayList<>(newestFirst.stream().map(MessagePersistenceAdapter::toDomain).toList());
        Collections.reverse(ascending);
        return ascending;
    }

    private static Message toDomain(MessageEntity e) {
        Set<MessageFlag> flags = EnumSet.noneOf(MessageFlag.class);
        if (e.getFlags() != null) {
            e.getFlags().forEach(f -> flags.add(MessageFlag.valueOf(f)));
        }
        return new Message(e.getId(), e.getConversationId(), e.getSeq(), e.getSenderId(), e.getClientMessageId(),
                e.getKind(), flags, e.getBody(), e.getCreatedAt());
    }
}
