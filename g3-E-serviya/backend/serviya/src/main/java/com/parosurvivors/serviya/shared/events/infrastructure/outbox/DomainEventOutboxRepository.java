package com.parosurvivors.serviya.shared.events.infrastructure.outbox;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DomainEventOutboxRepository extends JpaRepository<DomainEventOutboxEntity, Long> {

    @Query(value = "SELECT * FROM domain_event_outbox ORDER BY id LIMIT :limit FOR UPDATE SKIP LOCKED",
            nativeQuery = true)
    List<DomainEventOutboxEntity> claimBatch(@Param("limit") int limit);
}
