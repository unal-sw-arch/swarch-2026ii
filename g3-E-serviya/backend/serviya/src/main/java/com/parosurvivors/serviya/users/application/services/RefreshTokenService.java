package com.parosurvivors.serviya.users.application.services;

import com.parosurvivors.serviya.shared.audit.AuditEventType;
import com.parosurvivors.serviya.shared.audit.SecurityAuditServicePort;
import com.parosurvivors.serviya.shared.exceptions.UnauthorizedException;
import com.parosurvivors.serviya.users.application.dto.result.AuthResult;
import com.parosurvivors.serviya.users.application.dto.result.IssuedToken;
import com.parosurvivors.serviya.users.application.ports.input.TokenSessionServicePort;
import com.parosurvivors.serviya.users.application.ports.output.RefreshTokenPort;
import com.parosurvivors.serviya.users.application.ports.output.TokenProviderPort;
import com.parosurvivors.serviya.users.application.ports.output.UserReadPort;
import com.parosurvivors.serviya.users.domain.RefreshToken;
import com.parosurvivors.serviya.users.domain.RoleName;
import com.parosurvivors.serviya.users.domain.User;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manejo de sesión con refresh tokens rotativos (Fase 2). Abre familias (login/registro), las rota con
 * detección de reuso y ventana de gracia, y las revoca (logout). El valor en claro del refresh se genera
 * aquí (256 bits, {@link SecureRandom}) y solo se persiste su hash SHA-256.
 *
 * <p><b>Rotación:</b> fuera de la gracia, cada refresh marca el token usado como rotado y emite uno nuevo
 * en la misma familia (con el mismo vencimiento absoluto). Dentro de la gracia (varias pestañas) se reemite
 * solo el access y se conserva el mismo refresh. Reusar un token ya rotado o revocado = robo ⇒ se revoca la
 * familia entera. Siempre se relee la cuenta (baneado/eliminado ⇒ revoca la familia) y los roles desde BD.
 */
@Component
public class RefreshTokenService implements TokenSessionServicePort {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
    private static final int RAW_TOKEN_BYTES = 32; // 256 bits de entropía
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenPort refreshTokenPort;
    private final TokenProviderPort tokenProvider;
    private final UserReadPort userReadPort;
    /** Vida absoluta de la familia y ventana de gracia (de config; no se importa infraestructura). */
    private final Duration refreshTtl;
    private final Duration refreshGrace;
    private final SecurityAuditServicePort auditService;

    public RefreshTokenService(RefreshTokenPort refreshTokenPort,
                               TokenProviderPort tokenProvider,
                               UserReadPort userReadPort,
                               SecurityAuditServicePort auditService,
                               @Value("${serviya.security.jwt.refresh-ttl:30d}") Duration refreshTtl,
                               @Value("${serviya.security.jwt.refresh-grace:15s}") Duration refreshGrace) {
        this.refreshTokenPort = refreshTokenPort;
        this.tokenProvider = tokenProvider;
        this.userReadPort = userReadPort;
        this.auditService = auditService;
        this.refreshTtl = refreshTtl;
        this.refreshGrace = refreshGrace;
    }

    @Override
    @Transactional
    public AuthResult startSession(Long userId, List<RoleName> roles) {
        LocalDateTime now = LocalDateTime.now();
        String rawRefresh = generateRawToken();
        RefreshToken token = RefreshToken.builder()
                .userId(userId)
                .familyId(UUID.randomUUID().toString())
                .tokenHash(sha256Hex(rawRefresh))
                .issuedAt(now)
                .expiresAt(now.plus(refreshTtl))
                .build();
        RefreshToken saved = refreshTokenPort.save(token);

        IssuedToken access = tokenProvider.issue(userId, roles);
        return new AuthResult(access.token(), userId, roles, access.expiresAt(),
                rawRefresh, saved.getExpiresAt());
    }

    // noRollbackFor: varios caminos REVOCAN la familia y luego lanzan UnauthorizedException (reuso detectado,
    // cuenta baneada/eliminada). Sin esto, el @Transactional revertiría el revokeFamily al propagarse la
    // excepción y la familia robada NO quedaría revocada. Con noRollbackFor la revocación se CONFIRMA; los
    // caminos que lanzan ANTES de tocar la BD (token ausente/inválido/caducado) confirman una tx vacía.
    @Override
    @Transactional(noRollbackFor = UnauthorizedException.class)
    public AuthResult refresh(String rawRefreshToken, String userAgent, String ip) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new UnauthorizedException("Missing refresh token");
        }
        LocalDateTime now = LocalDateTime.now();
        RefreshToken token = refreshTokenPort.findByTokenHash(sha256Hex(rawRefreshToken))
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));

        if (token.isRevoked()) {
            throw new UnauthorizedException("Refresh token revoked");
        }
        if (token.isRotated()) {
            // Reuso de un token ya rotado: señal de robo. Se revoca la familia entera.
            refreshTokenPort.revokeFamily(token.getFamilyId(), now);
            log.warn("Refresh token reuse detected for family {}; family revoked", token.getFamilyId());
            auditService.record(AuditEventType.REFRESH_REUSE_DETECTED, token.getUserId(), false,
                    "family " + token.getFamilyId());
            throw new UnauthorizedException("Refresh token reuse detected");
        }
        if (token.isExpired(now)) {
            throw new UnauthorizedException("Refresh token expired");
        }

        // Relee la cuenta y los roles desde BD (no desde el token): baneado/eliminado ⇒ fin de la familia.
        User user = userReadPort.findById(token.getUserId())
                .filter(u -> !u.isBanned() && !u.isDeleted())
                .orElseGet(() -> {
                    refreshTokenPort.revokeFamily(token.getFamilyId(), now);
                    return null;
                });
        if (user == null) {
            throw new UnauthorizedException("Account is not active");
        }
        List<RoleName> roles = user.getRoles();
        IssuedToken access = tokenProvider.issue(user.getId(), roles);
        auditService.record(AuditEventType.REFRESH, user.getId());

        // Dentro de la gracia (varias pestañas refrescando casi a la vez): reemite solo el access y
        // conserva el mismo refresh, para no revocar a un cliente legítimo por una carrera benigna.
        if (token.withinGrace(now, refreshGrace)) {
            return new AuthResult(access.token(), user.getId(), roles, access.expiresAt(),
                    rawRefreshToken, token.getExpiresAt());
        }

        // Rotación normal: nuevo refresh en la misma familia, con el MISMO vencimiento absoluto.
        String newRaw = generateRawToken();
        RefreshToken rotated = refreshTokenPort.save(RefreshToken.builder()
                .userId(user.getId())
                .familyId(token.getFamilyId())
                .tokenHash(sha256Hex(newRaw))
                .issuedAt(now)
                .expiresAt(token.getExpiresAt())
                .userAgent(userAgent)
                .ip(ip)
                .build());

        if (refreshTokenPort.markReplaced(token.getId(), rotated.getId()) == 0) {
            // Otra petición rotó este token primero (carrera fuera de la gracia). Nuestro token nuevo
            // queda huérfano (su valor en claro no se entrega) y caduca; el cliente debe reautenticarse.
            throw new UnauthorizedException("Refresh token already rotated");
        }
        return new AuthResult(access.token(), user.getId(), roles, access.expiresAt(),
                newRaw, rotated.getExpiresAt());
    }

    @Override
    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return; // nada que revocar; el controller limpia la cookie igualmente
        }
        Optional<RefreshToken> token = refreshTokenPort.findByTokenHash(sha256Hex(rawRefreshToken));
        token.ifPresent(t -> {
            refreshTokenPort.revokeFamily(t.getFamilyId(), LocalDateTime.now());
            auditService.record(AuditEventType.LOGOUT, t.getUserId());
        });
    }

    @Override
    @Transactional
    public int deleteExpiredOrRevoked(LocalDateTime cutoff) {
        return refreshTokenPort.deleteExpiredOrRevokedBefore(cutoff);
    }

    private static String generateRawToken() {
        byte[] bytes = new byte[RAW_TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}
