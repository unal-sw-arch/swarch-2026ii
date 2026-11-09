package com.panaderia.identity.users

import com.panaderia.identity.audit.AuditService
import com.panaderia.identity.auth.RefreshTokenService
import com.panaderia.identity.domain.Role
import com.panaderia.identity.domain.User
import com.panaderia.identity.repository.RoleRepository
import com.panaderia.identity.repository.UserRepository
import com.panaderia.identity.security.AuthenticatedUser
import com.panaderia.identity.web.ApiException
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class UserService(
    private val userRepository: UserRepository,
    private val roleRepository: RoleRepository,
    private val passwordEncoder: PasswordEncoder,
    private val refreshTokenService: RefreshTokenService,
    private val auditService: AuditService,
) {
    @Transactional(readOnly = true)
    fun list(actor: AuthenticatedUser): List<UserResponse> {
        val users = userRepository.findAllByOrderByCreatedAtDesc()
        return users
            .filter { canManage(actor, it) }
            .map { it.toResponse() }
    }

    @Transactional(readOnly = true)
    fun get(actor: AuthenticatedUser, id: UUID): UserResponse {
        val user = find(id)
        if (!canManage(actor, user)) throw forbidden()
        return user.toResponse()
    }

    @Transactional
    fun create(actor: AuthenticatedUser, request: UserCreateRequest): UserResponse {
        val email = normalize(request.email)
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw ApiException(
                HttpStatus.CONFLICT,
                "EMAIL_ALREADY_EXISTS",
                "Ya existe una cuenta con ese correo electrónico",
            )
        }

        val roles = resolveRoles(actor, request.roles)
        val user = User(
            email = email,
            passwordHash = passwordEncoder.encode(request.password),
            fullName = request.full_name.trim(),
            active = true,
            roles = roles.toMutableSet(),
        )
        userRepository.save(user)
        auditService.record(
            actorId = actor.id,
            action = "USER_CREATED",
            entity = "user",
            entityId = user.id?.toString(),
            details = mapOf("email" to email, "roles" to roles.map { it.name }),
        )
        return user.toResponse()
    }

    @Transactional
    fun update(actor: AuthenticatedUser, id: UUID, request: UserUpdateRequest): UserResponse {
        val user = find(id)
        if (!canManage(actor, user)) throw forbidden()

        val changed = linkedMapOf<String, Any?>()

        request.email?.let { newEmail ->
            val normalized = normalize(newEmail)
            if (normalized != user.email) {
                if (userRepository.existsByEmailIgnoreCase(normalized)) {
                    throw ApiException(
                        HttpStatus.CONFLICT,
                        "EMAIL_ALREADY_EXISTS",
                        "Ya existe una cuenta con ese correo electrónico",
                    )
                }
                user.email = normalized
                changed["email"] = normalized
            }
        }

        request.full_name?.let { name ->
            val trimmed = name.trim()
            if (trimmed.isNotEmpty() && trimmed != user.fullName) {
                user.fullName = trimmed
                changed["full_name"] = trimmed
            }
        }

        request.password?.let {
            user.passwordHash = passwordEncoder.encode(it)
            changed["password"] = "updated"
        }

        request.active?.let { active ->
            if (!active && user.id == actor.id) {
                throw ApiException(
                    HttpStatus.CONFLICT,
                    "INVALID_STATE",
                    "No puedes desactivar tu propia cuenta",
                )
            }
            if (active != user.active) {
                user.active = active
                changed["active"] = active
                if (!active) refreshTokenService.revokeAll(requireNotNull(user.id))
            }
        }

        userRepository.save(user)
        if (changed.isNotEmpty()) {
            auditService.record(
                actorId = actor.id,
                action = "USER_UPDATED",
                entity = "user",
                entityId = user.id?.toString(),
                details = changed,
            )
        }
        return user.toResponse()
    }

    @Transactional
    fun assignRole(actor: AuthenticatedUser, id: UUID, roleName: String): UserResponse {
        val user = find(id)
        val role = roleRepository.findByNameIgnoreCase(roleName.trim())
            ?: throw ApiException(HttpStatus.BAD_REQUEST, "UNKNOWN_ROLE", "El rol no existe")

        if (user.roles.none { it.id == role.id }) {
            user.roles.add(role)
            userRepository.save(user)
            auditService.record(
                actorId = actor.id,
                action = "ROLE_ASSIGNED",
                entity = "user",
                entityId = user.id?.toString(),
                details = mapOf("role" to role.name),
            )
        }
        return user.toResponse()
    }

    @Transactional
    fun removeRole(actor: AuthenticatedUser, id: UUID, roleName: String): UserResponse {
        val user = find(id)
        val role = user.roles.firstOrNull { it.name.equals(roleName.trim(), ignoreCase = true) }
            ?: throw ApiException(HttpStatus.NOT_FOUND, "ROLE_NOT_ASSIGNED", "El usuario no tiene ese rol")

        if (user.roles.size <= 1) {
            throw ApiException(
                HttpStatus.CONFLICT,
                "INVALID_STATE",
                "La cuenta debe conservar al menos un rol",
            )
        }

        user.roles.remove(role)
        userRepository.save(user)
        auditService.record(
            actorId = actor.id,
            action = "ROLE_REVOKED",
            entity = "user",
            entityId = user.id?.toString(),
            details = mapOf("role" to role.name),
        )
        return user.toResponse()
    }

    private fun resolveRoles(actor: AuthenticatedUser, requested: List<String>): List<Role> {
        val names = (requested.ifEmpty { listOf("mesero") })
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .distinct()

        if (!actor.isAdmin() && names.any { it !in OPERATIONAL_ROLES }) {
            throw ApiException(
                HttpStatus.FORBIDDEN,
                "FORBIDDEN",
                "Un supervisor solo puede crear cuentas operativas",
            )
        }

        val roles = roleRepository.findAllByNameIn(names)
        if (roles.size != names.size) {
            throw ApiException(HttpStatus.BAD_REQUEST, "UNKNOWN_ROLE", "Alguno de los roles no existe")
        }
        return roles
    }

    private fun canManage(actor: AuthenticatedUser, user: User): Boolean {
        if (actor.isAdmin()) return true
        if (!actor.has("users:manage_operational")) return false
        return isOperational(user)
    }

    private fun isOperational(user: User): Boolean =
        user.roles.isNotEmpty() && user.roles.all { it.name in OPERATIONAL_ROLES }

    private fun find(id: UUID): User =
        userRepository.findById(id).orElseThrow {
            ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "El usuario no existe")
        }

    private fun forbidden(): ApiException =
        ApiException(
            HttpStatus.FORBIDDEN,
            "FORBIDDEN",
            "El rol o permiso no alcanza para gestionar esta cuenta",
        )

    private fun normalize(email: String): String = email.trim().lowercase()

    companion object {
        /** Roles operativos que un supervisor puede gestionar. */
        val OPERATIONAL_ROLES = setOf("mesero", "cocinero", "despachador")
    }
}
