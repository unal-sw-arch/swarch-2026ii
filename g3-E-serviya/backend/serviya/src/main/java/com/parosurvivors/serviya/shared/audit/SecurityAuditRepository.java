package com.parosurvivors.serviya.shared.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SecurityAuditRepository extends JpaRepository<SecurityAuditEntity, Long> {
}
