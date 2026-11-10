package com.parosurvivors.serviya.users.application.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.parosurvivors.serviya.shared.exceptions.UnauthorizedException;
import com.parosurvivors.serviya.users.application.dto.result.AuthResult;
import com.parosurvivors.serviya.users.application.dto.result.IssuedToken;
import com.parosurvivors.serviya.users.application.ports.output.RefreshTokenPort;
import com.parosurvivors.serviya.users.application.ports.output.TokenProviderPort;
import com.parosurvivors.serviya.users.application.ports.output.UserReadPort;
import com.parosurvivors.serviya.users.domain.RefreshToken;
import com.parosurvivors.serviya.users.domain.RoleName;
import com.parosurvivors.serviya.users.domain.User;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Rotación de refresh tokens (Fase 2): detección de reuso, ventana de gracia, relectura de cuenta/roles
 * y que el valor en claro nunca se persiste.
 */
@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock private RefreshTokenPort refreshTokenPort;
    @Mock private TokenProviderPort tokenProvider;
    @Mock private UserReadPort userReadPort;
    @Mock private com.parosurvivors.serviya.shared.audit.SecurityAuditServicePort auditService;

    private RefreshTokenService service;

    @BeforeEach
    void setUp() {
        service = new RefreshTokenService(refreshTokenPort, tokenProvider, userReadPort, auditService,
                Duration.ofDays(30), Duration.ofSeconds(15));
    }

    private void stubSaveAssigningIds() {
        AtomicLong ids = new AtomicLong(100);
        when(refreshTokenPort.save(any())).thenAnswer(inv -> {
            RefreshToken t = inv.getArgument(0);
            return RefreshToken.builder()
                    .id(ids.getAndIncrement())
                    .userId(t.getUserId()).familyId(t.getFamilyId()).tokenHash(t.getTokenHash())
                    .issuedAt(t.getIssuedAt()).expiresAt(t.getExpiresAt())
                    .userAgent(t.getUserAgent()).ip(t.getIp()).build();
        });
    }

    private static User activeUser(Long id, RoleName... roles) {
        return User.builder().id(id).banned(false).deletedAt(null).roles(List.of(roles)).build();
    }

    @Test
    void startSession_issuesAccessAndRefresh_storingOnlyTheHash() {
        stubSaveAssigningIds();
        when(tokenProvider.issue(eq(42L), anyList()))
                .thenReturn(new IssuedToken("access-jwt", LocalDateTime.now().plusMinutes(15)));

        AuthResult result = service.startSession(42L, List.of(RoleName.CLIENT));

        assertThat(result.token()).isEqualTo("access-jwt");
        assertThat(result.refreshToken()).isNotBlank();
        assertThat(result.refreshExpiresAt()).isNotNull();

        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenPort).save(saved.capture());
        // El token guardado lleva el HASH (64 hex), nunca el valor en claro devuelto al cliente.
        assertThat(saved.getValue().getTokenHash())
                .hasSize(64)
                .isNotEqualTo(result.refreshToken());
    }

    @Test
    void refresh_rotatesOutsideGrace_andReReadsRoles() {
        stubSaveAssigningIds();
        RefreshToken active = RefreshToken.builder()
                .id(1L).userId(42L).familyId("F").tokenHash("hashA")
                .issuedAt(LocalDateTime.now().minusMinutes(5))
                .expiresAt(LocalDateTime.now().plusDays(30)).build();
        when(refreshTokenPort.findByTokenHash(anyString())).thenReturn(Optional.of(active));
        when(userReadPort.findById(42L)).thenReturn(Optional.of(activeUser(42L, RoleName.CLIENT)));
        when(tokenProvider.issue(eq(42L), anyList()))
                .thenReturn(new IssuedToken("access-jwt", LocalDateTime.now().plusMinutes(15)));
        when(refreshTokenPort.markReplaced(eq(1L), anyLong())).thenReturn(1);

        AuthResult result = service.refresh("rawA", "ua", null);

        assertThat(result.token()).isEqualTo("access-jwt");
        assertThat(result.refreshToken()).isNotBlank().isNotEqualTo("rawA"); // refresh NUEVO
        assertThat(result.refreshExpiresAt()).isEqualTo(active.getExpiresAt()); // caducidad absoluta
        verify(refreshTokenPort).markReplaced(eq(1L), anyLong());

        // Los roles del nuevo access salen de la BD, no del token.
        ArgumentCaptor<List<RoleName>> roles = ArgumentCaptor.forClass(List.class);
        verify(tokenProvider).issue(eq(42L), roles.capture());
        assertThat(roles.getValue()).containsExactly(RoleName.CLIENT);
    }

    @Test
    void refresh_withinGrace_keepsSameRefresh_withoutRotating() {
        RefreshToken fresh = RefreshToken.builder()
                .id(1L).userId(42L).familyId("F").tokenHash("hashA")
                .issuedAt(LocalDateTime.now()) // dentro de los 15s de gracia
                .expiresAt(LocalDateTime.now().plusDays(30)).build();
        when(refreshTokenPort.findByTokenHash(anyString())).thenReturn(Optional.of(fresh));
        when(userReadPort.findById(42L)).thenReturn(Optional.of(activeUser(42L, RoleName.CLIENT)));
        when(tokenProvider.issue(eq(42L), anyList()))
                .thenReturn(new IssuedToken("access-jwt", LocalDateTime.now().plusMinutes(15)));

        AuthResult result = service.refresh("rawA", "ua", null);

        assertThat(result.token()).isEqualTo("access-jwt");
        assertThat(result.refreshToken()).isEqualTo("rawA"); // mismo refresh
        verify(refreshTokenPort, never()).save(any());
        verify(refreshTokenPort, never()).markReplaced(anyLong(), anyLong());
    }

    @Test
    void refresh_reuseOfRotatedToken_revokesFamily_and401() {
        RefreshToken rotated = RefreshToken.builder()
                .id(1L).userId(42L).familyId("F").tokenHash("hashA")
                .issuedAt(LocalDateTime.now().minusMinutes(5))
                .expiresAt(LocalDateTime.now().plusDays(30))
                .replacedById(99L).build(); // ya fue rotado
        when(refreshTokenPort.findByTokenHash(anyString())).thenReturn(Optional.of(rotated));

        assertThatThrownBy(() -> service.refresh("rawA", "ua", null))
                .isInstanceOf(UnauthorizedException.class);
        verify(refreshTokenPort).revokeFamily(eq("F"), any());
        // El reuso (robo) se audita.
        verify(auditService).record(
                eq(com.parosurvivors.serviya.shared.audit.AuditEventType.REFRESH_REUSE_DETECTED),
                eq(42L), eq(false), anyString());
    }

    @Test
    void refresh_bannedUser_revokesFamily_and401() {
        RefreshToken active = RefreshToken.builder()
                .id(1L).userId(42L).familyId("F").tokenHash("hashA")
                .issuedAt(LocalDateTime.now().minusMinutes(5))
                .expiresAt(LocalDateTime.now().plusDays(30)).build();
        when(refreshTokenPort.findByTokenHash(anyString())).thenReturn(Optional.of(active));
        when(userReadPort.findById(42L))
                .thenReturn(Optional.of(User.builder().id(42L).banned(true).build()));

        assertThatThrownBy(() -> service.refresh("rawA", "ua", null))
                .isInstanceOf(UnauthorizedException.class);
        verify(refreshTokenPort).revokeFamily(eq("F"), any());
    }

    @Test
    void refresh_revokedToken_401() {
        RefreshToken revoked = RefreshToken.builder()
                .id(1L).userId(42L).familyId("F").tokenHash("hashA")
                .issuedAt(LocalDateTime.now().minusMinutes(5))
                .expiresAt(LocalDateTime.now().plusDays(30))
                .revokedAt(LocalDateTime.now().minusMinutes(1)).build();
        when(refreshTokenPort.findByTokenHash(anyString())).thenReturn(Optional.of(revoked));

        assertThatThrownBy(() -> service.refresh("rawA", "ua", null))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void logout_revokesFamily() {
        RefreshToken token = RefreshToken.builder()
                .id(1L).userId(42L).familyId("F").tokenHash("hashA")
                .issuedAt(LocalDateTime.now()).expiresAt(LocalDateTime.now().plusDays(30)).build();
        when(refreshTokenPort.findByTokenHash(anyString())).thenReturn(Optional.of(token));

        service.logout("rawA");

        verify(refreshTokenPort).revokeFamily(eq("F"), any());
    }

    @Test
    void refresh_missingToken_401_withoutHittingTheStore() {
        assertThatThrownBy(() -> service.refresh("  ", "ua", null))
                .isInstanceOf(UnauthorizedException.class);
        verify(refreshTokenPort, never()).findByTokenHash(anyString());
    }
}
