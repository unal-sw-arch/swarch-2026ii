package com.panaderia.identity.repository

import com.panaderia.identity.domain.AuditLog
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface AuditLogRepository : JpaRepository<AuditLog, UUID> {
    fun findAllByOrderByCreatedAtDesc(): List<AuditLog>
    fun findAllByOrderByCreatedAtDesc(pageable: Pageable): List<AuditLog>
    fun findAllByActionOrderByCreatedAtDesc(action: String, pageable: Pageable): List<AuditLog>
}
