package com.parosurvivors.chat.directory.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.parosurvivors.chat.directory.application.services.UserProjectionService;
import com.parosurvivors.chat.directory.domain.UserStatus;
import com.parosurvivors.chat.support.Fakes;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class UserProjectionServiceTest {

    private static final Instant T0 = Instant.parse("2026-11-09T12:00:00Z");
    private final Fakes.InMemoryUsers users = new Fakes.InMemoryUsers();
    private final UserProjectionService service = new UserProjectionService(users);

    @Test
    void registro_crea_el_usuario_activo() {
        service.upsertProfile(5, "Ana", "http://a/1.png", T0);
        var u = users.find(5).orElseThrow();
        assertThat(u.displayName()).isEqualTo("Ana");
        assertThat(u.status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(u.canSendMessages()).isTrue();
    }

    @Test
    void actualizar_el_perfil_conserva_el_estado() {
        service.upsertProfile(5, "Ana", null, T0);
        service.suspend(5, T0.plusSeconds(1));
        service.upsertProfile(5, "Ana María", null, T0.plusSeconds(2));
        var u = users.find(5).orElseThrow();
        assertThat(u.displayName()).isEqualTo("Ana María");
        assertThat(u.status()).isEqualTo(UserStatus.SUSPENDED);
    }

    @Test
    void suspender_impide_enviar_y_reactivar_lo_devuelve() {
        service.upsertProfile(5, "Ana", null, T0);
        service.suspend(5, T0.plusSeconds(1));
        assertThat(users.find(5).orElseThrow().canSendMessages()).isFalse();
        service.reactivate(5, T0.plusSeconds(2));
        assertThat(users.find(5).orElseThrow().canSendMessages()).isTrue();
    }

    @Test
    void eliminar_anonimiza_y_no_se_revierte_con_eventos_posteriores() {
        service.upsertProfile(5, "Ana", "http://a/1.png", T0);
        service.delete(5, T0.plusSeconds(1));
        service.upsertProfile(5, "Ana otra vez", "http://a/2.png", T0.plusSeconds(2));
        service.reactivate(5, T0.plusSeconds(3));

        var u = users.find(5).orElseThrow();
        assertThat(u.displayName()).isEqualTo(UserProjectionService.DELETED_USER_NAME);
        assertThat(u.avatarUrl()).isNull();
        assertThat(u.status()).isEqualTo(UserStatus.DELETED);
    }

    @Test
    void un_evento_mas_viejo_que_la_fila_se_ignora() {
        service.upsertProfile(5, "Nuevo", null, T0.plusSeconds(10));
        service.upsertProfile(5, "Viejo", null, T0);
        service.suspend(5, T0);
        var u = users.find(5).orElseThrow();
        assertThat(u.displayName()).isEqualTo("Nuevo");
        assertThat(u.status()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void suspension_que_llega_antes_que_el_registro_crea_una_fila_provisional_que_el_registro_completa() {
        service.suspend(5, T0);
        assertThat(users.find(5).orElseThrow().status()).isEqualTo(UserStatus.SUSPENDED);
        service.upsertProfile(5, "Ana", null, T0.plusSeconds(1));
        var u = users.find(5).orElseThrow();
        assertThat(u.displayName()).isEqualTo("Ana");
        assertThat(u.status()).isEqualTo(UserStatus.SUSPENDED);
    }
}
