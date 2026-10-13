package com.parosurvivors.serviya.users.application.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.parosurvivors.serviya.shared.exceptions.InvalidStateException;
import com.parosurvivors.serviya.users.application.dto.result.IssuedResetToken;
import com.parosurvivors.serviya.users.application.dto.result.TokenValidationResult;
import com.parosurvivors.serviya.users.application.ports.output.PasswordResetTokenPersistencePort;
import com.parosurvivors.serviya.users.domain.PasswordResetToken;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Reglas del ciclo de vida del token de recuperación (RF-003): generación aleatoria, persistencia
 * SOLO del hash, expiración, un único token vivo por usuario, y consumo de un solo uso con error
 * genérico indistinguible entre inexistente / expirado / ya usado.
 */
@ExtendWith(MockitoExtension.class)
class PasswordResetTokenServiceTest {

    private static final long TTL_MINUTES = 30;
    private static final Long USER_ID = 7L;

    @Mock private PasswordResetTokenPersistencePort tokenPort;

    @InjectMocks private PasswordResetTokenService service;

    @BeforeEach
    void setTtl() {
        ReflectionTestUtils.setField(service, "tokenTtlMinutes", TTL_MINUTES);
    }

    private PasswordResetToken captureSaved() {
        ArgumentCaptor<PasswordResetToken> captor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokenPort).save(captor.capture());
        return captor.getValue();
    }

    // =====================================================
    // createToken
    // =====================================================

    @Test
    void createToken_persistsOnlyTheHashAndReturnsTheRawValue() {
        IssuedResetToken issued = service.createToken(USER_ID);
        PasswordResetToken saved = captureSaved();

        // 32 bytes en Base64 url-safe sin padding => 43 chars del alfabeto url-safe (apto para query param).
        assertThat(issued.rawToken()).matches("^[A-Za-z0-9_-]{43}$");
        // Lo que se guarda es el SHA-256 en hex, NUNCA el token en claro.
        assertThat(saved.getTokenHash()).matches("^[0-9a-f]{64}$");
        assertThat(saved.getTokenHash()).isNotEqualTo(issued.rawToken());
        assertThat(saved.getUserId()).isEqualTo(USER_ID);
        assertThat(saved.getUsedAt()).isNull();
        // created_at se asigna en el servicio (convención del proyecto, nunca @CreationTimestamp).
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void createToken_expiresAfterTheConfiguredTtl() {
        LocalDateTime before = LocalDateTime.now();
        IssuedResetToken issued = service.createToken(USER_ID);
        LocalDateTime after = LocalDateTime.now();

        assertThat(issued.expiresAt())
                .isAfterOrEqualTo(before.plusMinutes(TTL_MINUTES))
                .isBeforeOrEqualTo(after.plusMinutes(TTL_MINUTES));
        assertThat(captureSaved().getExpiresAt()).isEqualTo(issued.expiresAt());
    }

    @Test
    void createToken_burnsPreviousTokensSoOnlyOneLinkStaysUsable() {
        service.createToken(USER_ID);

        verify(tokenPort).invalidateAllForUser(eq(USER_ID), any(LocalDateTime.class));
    }

    @Test
    void createToken_generatesADifferentTokenEveryTime() {
        String first = service.createToken(USER_ID).rawToken();
        String second = service.createToken(USER_ID).rawToken();

        assertThat(first).isNotEqualTo(second);
    }

    // =====================================================
    // validateToken (solo lectura, NO consume)
    // =====================================================

    @Test
    void validateToken_findsTheTokenItJustIssued() {
        // Round-trip real: el hash guardado al emitir debe ser el mismo que se calcula al validar
        // (SHA-256 determinista; con BCrypt este lookup sería imposible).
        IssuedResetToken issued = service.createToken(USER_ID);
        String storedHash = captureSaved().getTokenHash();
        when(tokenPort.findByTokenHash(storedHash)).thenReturn(Optional.of(validToken()));

        assertThat(service.validateToken(issued.rawToken())).isEqualTo(TokenValidationResult.VALID);
    }

    @Test
    void validateToken_doesNotConsumeTheToken() {
        // Algunos clientes de correo previsualizan enlaces: abrir el link no debe quemarlo.
        when(tokenPort.findByTokenHash(any())).thenReturn(Optional.of(validToken()));

        service.validateToken("cualquier-token");

        verify(tokenPort, never()).update(any());
        verify(tokenPort, never()).invalidateAllForUser(any(), any());
    }

    @Test
    void validateToken_reportsUsedExpiredAndMissing() {
        when(tokenPort.findByTokenHash(any())).thenReturn(Optional.of(usedToken()));
        assertThat(service.validateToken("t")).isEqualTo(TokenValidationResult.USED);

        when(tokenPort.findByTokenHash(any())).thenReturn(Optional.of(expiredToken()));
        assertThat(service.validateToken("t")).isEqualTo(TokenValidationResult.EXPIRED);

        when(tokenPort.findByTokenHash(any())).thenReturn(Optional.empty());
        assertThat(service.validateToken("t")).isEqualTo(TokenValidationResult.NOT_FOUND);
    }

    @Test
    void validateToken_treatsBlankInputAsNotFound() {
        assertThat(service.validateToken(null)).isEqualTo(TokenValidationResult.NOT_FOUND);
        assertThat(service.validateToken("  ")).isEqualTo(TokenValidationResult.NOT_FOUND);
    }

    // =====================================================
    // consumeToken (verificación real + quemado)
    // =====================================================

    @Test
    void consumeToken_marksItUsedAndBurnsTheRest() {
        when(tokenPort.findByTokenHash(any())).thenReturn(Optional.of(validToken()));

        PasswordResetToken consumed = service.consumeToken("token-valido");

        assertThat(consumed.getUserId()).isEqualTo(USER_ID);
        assertThat(consumed.isUsed()).isTrue();
        verify(tokenPort).update(consumed);
        // Tras un reset exitoso ningún otro enlace del usuario debe seguir sirviendo.
        verify(tokenPort).invalidateAllForUser(eq(USER_ID), any(LocalDateTime.class));
    }

    @Test
    void consumeToken_rejectsMissingUsedAndExpiredWithTheSameGenericError() {
        // El mensaje debe ser idéntico en los tres casos: distinguirlos filtraría información
        // sobre la existencia y el estado de los tokens.
        when(tokenPort.findByTokenHash(any())).thenReturn(Optional.empty());
        String missing = messageOfRejection("t");

        when(tokenPort.findByTokenHash(any())).thenReturn(Optional.of(usedToken()));
        String used = messageOfRejection("t");

        when(tokenPort.findByTokenHash(any())).thenReturn(Optional.of(expiredToken()));
        String expired = messageOfRejection("t");

        assertThat(missing).isEqualTo(used).isEqualTo(expired);
        verify(tokenPort, never()).update(any());
    }

    @Test
    void consumeToken_rejectsBlankInput() {
        assertThatThrownBy(() -> service.consumeToken(null)).isInstanceOf(InvalidStateException.class);
        assertThatThrownBy(() -> service.consumeToken(" ")).isInstanceOf(InvalidStateException.class);
    }

    // =====================================================
    // limpieza
    // =====================================================

    @Test
    void deleteExpiredTokens_delegatesTheCutoff() {
        LocalDateTime cutoff = LocalDateTime.now();
        when(tokenPort.deleteExpiredBefore(cutoff)).thenReturn(4);

        assertThat(service.deleteExpiredTokens(cutoff)).isEqualTo(4);
    }

    // =====================================================
    // helpers
    // =====================================================

    private String messageOfRejection(String rawToken) {
        try {
            service.consumeToken(rawToken);
            throw new AssertionError("Se esperaba InvalidStateException");
        } catch (InvalidStateException e) {
            return e.getMessage();
        }
    }

    private PasswordResetToken validToken() {
        return PasswordResetToken.builder()
                .id(1L)
                .userId(USER_ID)
                .tokenHash("hash")
                .expiresAt(LocalDateTime.now().plusMinutes(TTL_MINUTES))
                .createdAt(LocalDateTime.now())
                .build();
    }

    private PasswordResetToken usedToken() {
        PasswordResetToken token = validToken();
        token.setUsedAt(LocalDateTime.now().minusMinutes(1));
        return token;
    }

    private PasswordResetToken expiredToken() {
        PasswordResetToken token = validToken();
        token.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        return token;
    }
}
