package com.parosurvivors.serviya.feedback.infrastructure.adapters.output;

import com.parosurvivors.serviya.feedback.application.ports.output.ServiceFeedbackTagPersistencePort;
import com.parosurvivors.serviya.feedback.infrastructure.entities.ServiceFeedbackTagEntity;
import com.parosurvivors.serviya.feedback.infrastructure.repositories.ServiceFeedbackTagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ServiceFeedbackTagPersistenceAdapter implements ServiceFeedbackTagPersistencePort {

    private final ServiceFeedbackTagRepository repository;

    @Override
    public void addTags(Long feedbackId, List<Long> tagIds) {
        if (tagIds == null) {
            return;
        }
        List<ServiceFeedbackTagEntity> links = tagIds.stream()
                .map(tagId -> {
                    ServiceFeedbackTagEntity entity = new ServiceFeedbackTagEntity();
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
                .map(ServiceFeedbackTagEntity::getTagId)
                .collect(Collectors.toList());
    }

    @Override
    public void deleteByFeedbackId(Long feedbackId) {
        repository.deleteAll(repository.findByFeedbackId(feedbackId));
    }
}
