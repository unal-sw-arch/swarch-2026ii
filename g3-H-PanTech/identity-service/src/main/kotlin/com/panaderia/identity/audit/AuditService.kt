package com.panaderia.identity.audit

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.panaderia.identity.domain.AuditLog
import com.panaderia.identity.repository.AuditLogRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

data class AuditLogResponse(
    val id: UUID,
    val actor_id: UUID?,
    val action: String,
    val entity: String,
    val entity_id: String?,
    val details: Map<String, Any?>?,
    val created_at: Instant,
)

@Service
class AuditService(
    private val repository: AuditLogRepository,
    private val objectMapper: ObjectMapper,
) {
    fun record(
        actorId: UUID?,
        action: String,
        entity: String,
        entityId: String? = null,
        details: Map<String, Any?> = emptyMap(),
    ) {
        val log = AuditLog(
            actorId = actorId,
            action = action,
            entity = entity,
            entityId = entityId,
            details = if (details.isEmpty()) null else objectMapper.writeValueAsString(details),
        )
        repository.save(log)
    }

    @Transactional(readOnly = true)
    fun list(limit: Int, action: String?): List<AuditLogResponse> {
        val pageable = PageRequest.of(0, limit.coerceIn(1, 500))
        val rows = if (action.isNullOrBlank()) {
            repository.findAllByOrderByCreatedAtDesc(pageable)
        } else {
            repository.findAllByActionOrderByCreatedAtDesc(action.trim().uppercase(), pageable)
        }
        return rows.map { row ->
            AuditLogResponse(
                id = requireNotNull(row.id),
                actor_id = row.actorId,
                action = row.action,
                entity = row.entity,
                entity_id = row.entityId,
                details = row.details?.let { objectMapper.readValue<Map<String, Any?>>(it) },
                created_at = row.createdAt,
            )
        }
    }
}
