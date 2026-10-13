package com.parosurvivors.serviya.feedback.infrastructure.repositories;

import com.parosurvivors.serviya.feedback.infrastructure.entities.ServiceFeedbackTagEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ServiceFeedbackTagRepository extends JpaRepository<ServiceFeedbackTagEntity, Long> {
    List<ServiceFeedbackTagEntity> findByFeedbackId(Long feedbackId);
}
