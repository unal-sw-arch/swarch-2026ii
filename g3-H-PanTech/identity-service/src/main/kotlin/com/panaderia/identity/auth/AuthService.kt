package com.panaderia.identity.auth

import com.panaderia.identity.audit.AuditService
import com.panaderia.identity.config.JwtProperties
import com.panaderia.identity.domain.User
import com.panaderia.identity.repository.RoleRepository
import com.panaderia.identity.repository.UserRepository
import com.panaderia.identity.users.UserResponse
import com.panaderia.identity.users.toResponse
import com.panaderia.identity.web.ApiException
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val roleRepository: RoleRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtService: JwtService,
    private val refreshTokenService: RefreshTokenService,
    private val auditService: AuditService,
    private val properties: JwtProperties,
) {
    @Transactional
    fun register(request: RegisterRequest): AuthResponse {
        val email = normalize(request.email)
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw ApiException(
                HttpStatus.CONFLICT,
                "EMAIL_ALREADY_EXISTS",
                "Ya existe una cuenta con ese correo electrónico",
            )
        }

        // El registro público siempre crea un cliente; los roles del cuerpo se ignoran.
        val clientRole = roleRepository.findByNameIgnoreCase("cliente")
            ?: throw IllegalStateException("Falta el rol semilla 'cliente'")

        val user = User(
            email = email,
            passwordHash = passwordEncoder.encode(request.password),
            fullName = request.full_name.trim(),
            active = true,
            roles = mutableSetOf(clientRole),
        )
        userRepository.save(user)
        auditService.record(
            actorId = user.id,
            action = "USER_REGISTERED",
            entity = "user",
            entityId = user.id?.toString(),
            details = mapOf("email" to email),
        )
        return tokens(user)
    }

    @Transactional
    fun login(request: LoginRequest): AuthResponse {
        val email = normalize(request.email)
        val user = userRepository.findByEmailIgnoreCase(email)

        if (user == null || !passwordEncoder.matches(request.password, user.passwordHash)) {
            auditService.record(
                actorId = user?.id,
                action = "LOGIN_FAILED",
                entity = "user",
                entityId = user?.id?.toString(),
                details = mapOf("email" to email),
            )
            throw ApiException(
                HttpStatus.UNAUTHORIZED,
                "INVALID_CREDENTIALS",
                "Correo o contraseña incorrectos",
            )
        }

        if (!user.active) {
            auditService.record(
                actorId = user.id,
                action = "LOGIN_FAILED",
                entity = "user",
                entityId = user.id?.toString(),
                details = mapOf("email" to email, "reason" to "account_disabled"),
            )
            throw ApiException(
                HttpStatus.UNAUTHORIZED,
                "ACCOUNT_DISABLED",
                "La cuenta está desactivada",
            )
        }

        user.lastLoginAt = Instant.now()
        userRepository.save(user)
        auditService.record(user.id, "LOGIN", "user", user.id?.toString())
        return tokens(user)
    }

    @Transactional
    fun refresh(rawRefreshToken: String?): AuthResponse {
        val raw = rawRefreshToken?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw ApiException(
                HttpStatus.UNAUTHORIZED,
                "INVALID_REFRESH_TOKEN",
                "Falta el refresh token",
            )
        val user = refreshTokenService.consume(raw)
        auditService.record(user.id, "TOKEN_REFRESHED", "user", user.id?.toString())
        return tokens(user)
    }

    @Transactional
    fun logout(rawRefreshToken: String?, actorId: UUID?) {
        refreshTokenService.revoke(rawRefreshToken)
        auditService.record(actorId, "LOGOUT", "user", actorId?.toString())
    }

    @Transactional(readOnly = true)
    fun me(actorId: UUID): UserResponse {
        val user = userRepository.findById(actorId).orElseThrow {
            ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "La cuenta ya no existe")
        }
        if (!user.active) {
            throw ApiException(HttpStatus.UNAUTHORIZED, "ACCOUNT_DISABLED", "La cuenta está desactivada")
        }
        return user.toResponse()
    }

    private fun tokens(user: User): AuthResponse {
        val access = jwtService.issueAccessToken(user)
        val refresh = refreshTokenService.issue(user)
        return AuthResponse(
            access_token = access.value,
            refresh_token = refresh,
            expires_in = access.expiresInSeconds,
            refresh_expires_in = properties.refreshTtlSeconds,
            user = user.toResponse(),
        )
    }

    private fun normalize(email: String): String = email.trim().lowercase()
}
