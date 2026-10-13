package com.parosurvivors.serviya.feedback.infrastructure.repositories;

import com.parosurvivors.serviya.feedback.infrastructure.entities.ServiceFeedbackTagCatalogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ServiceFeedbackTagCatalogRepository extends JpaRepository<ServiceFeedbackTagCatalogEntity, Long> {
}
