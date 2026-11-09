package com.panaderia.identity.auth

import com.fasterxml.jackson.annotation.JsonProperty
import com.panaderia.identity.users.UserResponse
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class RegisterRequest(
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
)

data class LoginRequest(
    @field:NotBlank
    @field:Email
    @field:Size(max = 255)
    val email: String,

    @field:NotBlank
    @field:Size(max = 72)
    val password: String,
)

data class RefreshRequest(
    @JsonProperty("refresh_token")
    val refresh_token: String? = null,
)

data class LogoutRequest(
    @JsonProperty("refresh_token")
    val refresh_token: String? = null,
)

data class AuthResponse(
    @JsonProperty("access_token") val access_token: String,
    @JsonProperty("refresh_token") val refresh_token: String,
    @JsonProperty("token_type") val token_type: String = "Bearer",
    @JsonProperty("expires_in") val expires_in: Long,
    @JsonProperty("refresh_expires_in") val refresh_expires_in: Long,
    val user: UserResponse,
)

data class MessageResponse(val message: String)
