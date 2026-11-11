package com.panaderia.identity.auth

import com.panaderia.identity.security.AuthenticatedUser
import com.panaderia.identity.security.authenticatedUser
import com.panaderia.identity.users.UserResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/auth")
class AuthController(
    private val authService: AuthService,
) {
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    fun register(@Valid @RequestBody request: RegisterRequest): AuthResponse =
        authService.register(request)

    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest): AuthResponse =
        authService.login(request)

    @PostMapping("/refresh")
    fun refresh(
        @RequestBody(required = false) request: RefreshRequest?,
        @CookieValue(name = "panaderia_refresh", required = false) cookie: String?,
    ): AuthResponse = authService.refresh(request?.refresh_token ?: cookie)

    @PostMapping("/logout")
    fun logout(
        @RequestBody(required = false) request: LogoutRequest?,
        @CookieValue(name = "panaderia_refresh", required = false) cookie: String?,
        authentication: Authentication,
    ): MessageResponse {
        val actor = authentication.authenticatedUser()
        authService.logout(request?.refresh_token ?: cookie, actor.id)
        return MessageResponse("Sesión cerrada")
    }

    @GetMapping("/me")
    fun me(authentication: Authentication): UserResponse {
        val actor: AuthenticatedUser = authentication.authenticatedUser()
        return authService.me(actor.id)
    }
}
