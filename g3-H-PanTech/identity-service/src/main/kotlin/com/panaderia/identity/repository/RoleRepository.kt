package com.panaderia.identity.repository

import com.panaderia.identity.domain.Role
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface RoleRepository : JpaRepository<Role, UUID> {
    fun findByNameIgnoreCase(name: String): Role?
    fun findAllByNameIn(names: Collection<String>): List<Role>
    fun findAllByOrderByLevelDescNameAsc(): List<Role>
}
