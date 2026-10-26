package com.parosurvivors.serviya.feedback.infrastructure.repositories;

import com.parosurvivors.serviya.feedback.infrastructure.entities.ClientFeedbackTagCatalogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ClientFeedbackTagCatalogRepository extends JpaRepository<ClientFeedbackTagCatalogEntity, Long> {
}
