package com.parosurvivors.serviya.users.infrastructure.adapters.input;

import com.parosurvivors.serviya.shared.security.CurrentUser;
import com.parosurvivors.serviya.users.application.dto.result.AuthResult;
import com.parosurvivors.serviya.users.application.ports.input.SessionRevocationServicePort;
import com.parosurvivors.serviya.users.application.ports.input.TokenSessionServicePort;
import com.parosurvivors.serviya.users.application.ports.input.UserAuthenticationServicePort;
import com.parosurvivors.serviya.users.infrastructure.adapters.input.api.AuthApi;
import com.parosurvivors.serviya.users.infrastructure.dto.form.ConfirmPasswordResetForm;
import com.parosurvivors.serviya.users.infrastructure.dto.form.LoginForm;
import com.parosurvivors.serviya.users.infrastructure.dto.form.RegisterUserForm;
import com.parosurvivors.serviya.users.infrastructure.dto.form.RequestPasswordResetForm;
import com.parosurvivors.serviya.users.infrastructure.dto.form.ValidatePasswordResetTokenForm;
import com.parosurvivors.serviya.users.infrastructure.dto.response.AuthResponse;
import com.parosurvivors.serviya.users.infrastructure.security.JwtProperties;
import com.parosurvivors.serviya.shared.exceptions.RateLimitExceededException;
import com.parosurvivors.serviya.shared.ratelimit.RateLimitDecision;
import com.parosurvivors.serviya.shared.ratelimit.RateLimiter;
import com.parosurvivors.serviya.users.infrastructure.mappers.UserWebMapper;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Adaptador de entrada (REST) de autenticacion. Placeholder funcional: enruta, mapea Form->Command
 * y Result->Response (UserWebMapper) y delega en {@link UserAuthenticationServicePort}.
 * La documentacion vive en {@link AuthApi}.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController implements AuthApi {

    private final UserAuthenticationServicePort authService;
    private final TokenSessionServicePort tokenSessionService;
    private final SessionRevocationServicePort sessionRevocationService;
    private final com.parosurvivors.serviya.shared.audit.SecurityAuditServicePort auditService;
    private final UserWebMapper mapper;
    private final RateLimiter rateLimiter;
    private final JwtProperties jwtProperties;

    /**
     * Normaliza el correo antes de usarlo como clave de cuota. Sin esto, {@code Ana@X.com} y
     * {@code ana@x.com} tendrian baldes distintos y bastaria alternar mayusculas para multiplicar
     * el limite de la misma cuenta.
     */
    private static String normalized(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    /** Consume una cuota por correo (D-09) y, si esta agotada, corta con 429 + {@code Retry-After}. */
    private void enforceEmailQuota(String policy, String email) {
        RateLimitDecision decision = rateLimiter.tryConsume(policy, "email", normalized(email));
        if (!decision.allowed()) {
            throw new RateLimitExceededException(decision.retryAfterSeconds());
        }
    }

    /**
     * Adjunta la cookie HttpOnly del refresh token a la respuesta, SOLO si el flujo emitio/roto un refresh
     * ({@code acquireRole} no lo hace). El access token va en el cuerpo; el refresh nunca (Fase 2).
     */
    private ResponseEntity.BodyBuilder withRefreshCookie(ResponseEntity.BodyBuilder builder, AuthResult result) {
        if (result.refreshToken() != null) {
            builder.header(HttpHeaders.SET_COOKIE,
                    buildRefreshCookie(result.refreshToken(), result.refreshExpiresAt()).toString());
        }
        return builder;
    }

    private ResponseCookie buildRefreshCookie(String rawRefresh, LocalDateTime expiresAt) {
        long maxAgeSeconds = expiresAt == null
                ? jwtProperties.getRefreshTtl().getSeconds()
                : Math.max(0, Duration.between(LocalDateTime.now(), expiresAt).getSeconds());
        return refreshCookie(rawRefresh, maxAgeSeconds);
    }

    /** Cookie vacia con {@code Max-Age=0}: indica al navegador que elimine el refresh (logout). */
    private ResponseCookie clearedRefreshCookie() {
        return refreshCookie("", 0);
    }

    private ResponseCookie refreshCookie(String value, long maxAgeSeconds) {
        return ResponseCookie.from(jwtProperties.getRefreshCookieName(), value)
                .httpOnly(true)
                .secure(jwtProperties.isRefreshCookieSecure())
                .path(jwtProperties.getRefreshCookiePath())
                .maxAge(maxAgeSeconds)
                .sameSite(jwtProperties.getRefreshCookieSameSite())
                .build();
    }

    @Override
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterUserForm form) {
        AuthResult result = authService.register(mapper.toCommand(form));
        return withRefreshCookie(ResponseEntity.status(HttpStatus.CREATED), result)
                .body(mapper.toResponse(result));
    }

    @Override
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginForm form) {
        // Sin cuota por correo a proposito (D-09): un limite por correo en login habilita un DoS de
        // bloqueo de la cuenta de la victima. La fuerza bruta por origen la frena la cuota por IP (filtro).
        AuthResult result = authService.login(mapper.toCommand(form));
        return withRefreshCookie(ResponseEntity.ok(), result).body(mapper.toResponse(result));
    }

    @Override
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @Parameter(hidden = true)
            @CookieValue(name = "${serviya.security.jwt.refresh-cookie-name:serviya_refresh}", required = false)
            String refreshCookie,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
        AuthResult result = tokenSessionService.refresh(refreshCookie, userAgent, null);
        return withRefreshCookie(ResponseEntity.ok(), result).body(mapper.toResponse(result));
    }

    @Override
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @Parameter(hidden = true)
            @CookieValue(name = "${serviya.security.jwt.refresh-cookie-name:serviya_refresh}", required = false)
            String refreshCookie) {
        tokenSessionService.logout(refreshCookie);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, clearedRefreshCookie().toString())
                .build();
    }

    @Override
    @PostMapping("/logout-all")
    public ResponseEntity<Void> logoutAll() {
        // La identidad sale del access token (CurrentUser.id()), nunca de la cookie: cerrar TODAS las
        // sesiones es una accion autenticada. Revoca todas las familias de refresh + escribe min_iat.
        sessionRevocationService.revokeAllSessions(CurrentUser.id());
        auditService.record(com.parosurvivors.serviya.shared.audit.AuditEventType.LOGOUT_ALL, CurrentUser.id());
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, clearedRefreshCookie().toString())
                .build();
    }

    @Override
    @PostMapping("/password-reset")
    public ResponseEntity<Void> requestPasswordReset(@Valid @RequestBody RequestPasswordResetForm form) {
        enforceEmailQuota("reset-email", form.email());
        authService.requestPasswordReset(mapper.toCommand(form));
        return ResponseEntity.accepted().build();
    }

    @Override
    @PostMapping("/password-reset/validate")
    public ResponseEntity<Void> validatePasswordResetToken(
            @Parameter(description = "Token recibido en el enlace del correo", required = true)
            @Valid @RequestBody ValidatePasswordResetTokenForm form) {
        authService.validatePasswordResetToken(form.token());
        return ResponseEntity.noContent().build();
    }

    @Override
    @PostMapping("/password-reset/confirm")
    public ResponseEntity<Void> confirmPasswordReset(@Valid @RequestBody ConfirmPasswordResetForm form) {
        authService.confirmPasswordReset(mapper.toCommand(form));
        return ResponseEntity.noContent().build();
    }
}
