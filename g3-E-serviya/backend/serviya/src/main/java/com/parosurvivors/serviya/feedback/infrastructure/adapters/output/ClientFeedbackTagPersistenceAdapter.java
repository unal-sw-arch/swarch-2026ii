package com.parosurvivors.serviya.feedback.infrastructure.adapters.output;

import com.parosurvivors.serviya.feedback.application.ports.output.ClientFeedbackTagPersistencePort;
import com.parosurvivors.serviya.feedback.infrastructure.entities.ClientFeedbackTagEntity;
import com.parosurvivors.serviya.feedback.infrastructure.repositories.ClientFeedbackTagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ClientFeedbackTagPersistenceAdapter implements ClientFeedbackTagPersistencePort {

    private final ClientFeedbackTagRepository repository;

    @Override
    public void addTags(Long feedbackId, List<Long> tagIds) {
        if (tagIds == null) {
            return;
        }
        List<ClientFeedbackTagEntity> links = tagIds.stream()
                .map(tagId -> {
                    ClientFeedbackTagEntity entity = new ClientFeedbackTagEntity();
                    entity.setFeedbackId(feedbackId);
                    entity.setTagId(tagId);
                    return entity;
                })
                .collect(Collectors.toList());
        repository.saveAll(links);
    }

    @Override
    public List<Long> findTagIdsByFeedbackId(Long feedbackId) {
        return repository.findByFeedbackId(feedbackId).stream()
                .map(ClientFeedbackTagEntity::getTagId)
                .collect(Collectors.toList());
    }

    @Override
    public void deleteByFeedbackId(Long feedbackId) {
        repository.deleteAll(repository.findByFeedbackId(feedbackId));
    }
}
