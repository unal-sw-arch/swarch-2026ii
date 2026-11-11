package com.panaderia.identity.users

import com.fasterxml.jackson.annotation.JsonProperty
import com.panaderia.identity.domain.User
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class UserResponse(
    val id: UUID,
    val email: String,
    @JsonProperty("full_name") val full_name: String,
    val active: Boolean,
    val roles: List<String>,
    val permissions: List<String>,
    val level: Int,
    @JsonProperty("created_at") val created_at: Instant,
    @JsonProperty("last_login_at") val last_login_at: Instant?,
)

fun User.toResponse(): UserResponse = UserResponse(
    id = requireNotNull(id) { "El usuario no está persistido" },
    email = email,
    full_name = fullName,
    active = active,
    roles = roleNames(),
    permissions = effectivePermissions().toList(),
    level = effectiveLevel(),
    created_at = createdAt,
    last_login_at = lastLoginAt,
)

data class UserCreateRequest(
    @field:NotBlank
    @field:Email
    @field:Size(max = 255)
    val email: String,

    @field:NotBlank
    @field:Size(min = 8, max = 72)
    val password: String,

    @JsonProperty("full_name")
    @field:NotBlank
    @field:Size(max = 255)
    val full_name: String,

    val roles: List<String> = emptyList(),
)

data class UserUpdateRequest(
    @field:Email
    @field:Size(max = 255)
    val email: String? = null,

    @JsonProperty("full_name")
    @field:Size(max = 255)
    val full_name: String? = null,

    val active: Boolean? = null,

    @field:Size(min = 8, max = 72)
    val password: String? = null,
)

data class RoleAssignmentRequest(
    @field:NotBlank
    @field:Size(max = 50)
    val role: String,
)
