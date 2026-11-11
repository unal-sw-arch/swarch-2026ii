package com.panaderia.identity.repository

import com.panaderia.identity.domain.Permission
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface PermissionRepository : JpaRepository<Permission, UUID> {
    fun findAllByOrderByCodeAsc(): List<Permission>
    fun findByCode(code: String): Permission?
}
