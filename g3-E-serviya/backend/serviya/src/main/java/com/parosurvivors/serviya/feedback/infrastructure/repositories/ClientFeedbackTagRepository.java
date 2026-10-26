package com.parosurvivors.serviya.feedback.infrastructure.repositories;

import com.parosurvivors.serviya.feedback.infrastructure.entities.ClientFeedbackTagEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ClientFeedbackTagRepository extends JpaRepository<ClientFeedbackTagEntity, Long> {
    List<ClientFeedbackTagEntity> findByFeedbackId(Long feedbackId);
}
