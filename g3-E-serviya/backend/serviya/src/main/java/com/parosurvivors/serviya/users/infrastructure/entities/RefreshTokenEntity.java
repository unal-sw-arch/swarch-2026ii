package com.parosurvivors.serviya.users.infrastructure.entities;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * Refresh token (Fase 2). El valor en claro nunca se persiste: solo su SHA-256 ({@code token_hash}, único).
 * {@code family_id} agrupa la cadena de rotaciones; {@code replaced_by_id} apunta al token que lo rotó.
 */
@Entity
@Table(name = "refresh_tokens", indexes = {
        @Index(name = "idx_refresh_family", columnList = "family_id"),
        @Index(name = "idx_refresh_user", columnList = "user_id")
})
@Getter
@Setter
public class RefreshTokenEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Integer version;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "family_id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
    private String familyId;

    /** SHA-256 en hex del token; UNIQUE. */
    @Column(name = "token_hash", nullable = false, unique = true, length = 64, columnDefinition = "CHAR(64)")
    private String tokenHash;

    @Column(name = "issued_at", nullable = false)
    private LocalDateTime issuedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "replaced_by_id")
    private Long replacedById;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @Column(name = "ip", length = 64)
    private String ip;
}
