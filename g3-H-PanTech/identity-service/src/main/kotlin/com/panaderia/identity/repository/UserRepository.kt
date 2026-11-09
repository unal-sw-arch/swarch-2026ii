package com.panaderia.identity.repository

import com.panaderia.identity.domain.User
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface UserRepository : JpaRepository<User, UUID> {
    fun findByEmailIgnoreCase(email: String): User?
    fun existsByEmailIgnoreCase(email: String): Boolean
    fun findAllByOrderByCreatedAtDesc(): List<User>
}
