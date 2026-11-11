package com.panaderia.identity.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "audit_log", schema = "auth")
class AuditLog(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @Column(name = "actor_id")
    var actorId: UUID? = null,

    @Column(nullable = false, length = 80)
    var action: String = "",

    @Column(nullable = false, length = 80)
    var entity: String = "",

    @Column(name = "entity_id", length = 120)
    var entityId: String? = null,

    @Column(columnDefinition = "text")
    var details: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now(),
)
