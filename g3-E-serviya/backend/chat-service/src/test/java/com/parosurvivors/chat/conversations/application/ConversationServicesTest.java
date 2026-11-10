package com.parosurvivors.chat.conversations.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.parosurvivors.chat.conversations.application.dto.ConversationCursor;
import com.parosurvivors.chat.conversations.application.dto.ConversationFilter;
import com.parosurvivors.chat.conversations.application.dto.ConversationPage;
import com.parosurvivors.chat.conversations.application.dto.ConversationSummary;
import com.parosurvivors.chat.conversations.application.services.ApplyRequestStatusService;
import com.parosurvivors.chat.conversations.application.services.GetConversationService;
import com.parosurvivors.chat.conversations.application.services.ListMyConversationsService;
import com.parosurvivors.chat.conversations.application.services.MarkAsReadService;
import com.parosurvivors.chat.conversations.application.services.OpenRequestConversationService;
import com.parosurvivors.chat.conversations.domain.Conversation;
import com.parosurvivors.chat.conversations.domain.ConversationStatus;
import com.parosurvivors.chat.conversations.domain.ConversationType;
import com.parosurvivors.chat.conversations.domain.TerminalRequestStatuses;
import com.parosurvivors.chat.messaging.domain.MessageKind;
import com.parosurvivors.chat.shared.application.ChatEvents;
import com.parosurvivors.chat.support.Fakes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConversationServicesTest {

    private static final long CLIENT = 10;
    private static final long OFFERER = 20;
    private static final long STRANGER = 99;
    private static final Instant T0 = Instant.parse("2026-11-09T12:00:00Z");
    private static final TerminalRequestStatuses TERMINAL =
            TerminalRequestStatuses.of("REJECTED", "CANCELLED", "COMPLETED", "RESCHEDULED", "NOT_PROVIDED");

    private final List<String> timeline = new ArrayList<>();
    private final Fakes.InMemoryConversations conversations = new Fakes.InMemoryConversations();
    private final Fakes.RecordingEvents events = new Fakes.RecordingEvents();
    private final Fakes.RecordingRealtime realtime = new Fakes.RecordingRealtime();
    private OpenRequestConversationService open;
    private ApplyRequestStatusService applyStatus;

    @BeforeEach
    void setUp() {
        realtime.log = timeline;
        var uow = new Fakes.DirectUnitOfWork(timeline);
        open = new OpenRequestConversationService(conversations, events, realtime, uow, TERMINAL);
        applyStatus = new ApplyRequestStatusService(conversations, events, realtime, uow, TERMINAL);
    }

    private OpenRequestConversationService.Result openRequest(long requestId) {
        return open.execute(new OpenRequestConversationService.Command(requestId, CLIENT, OFFERER, "PENDING", T0));
    }

    // --- OpenRequestConversation (RF-CHAT-01/02) ---

    @Test
    void al_crearse_la_solicitud_se_crea_la_conversacion_y_se_avisa_a_ambos() {
        var result = openRequest(7);

        assertThat(result.created()).isTrue();
        assertThat(result.conversation().id()).isNotNull();
        assertThat(conversations.store).hasSize(1);
        assertThat(realtime.recipientsByEvent.get("conversation.created")).containsExactlyInAnyOrder(CLIENT, OFFERER);
        var created = events.of(ChatEvents.CONVERSATION_CREATED);
        assertThat(created).hasSize(1);
        assertThat(created.get(0).data()).containsEntry("context_id", 7L).containsEntry("type", "REQUEST");
    }

    @Test
    void el_mismo_evento_dos_veces_no_crea_una_segunda_conversacion_ni_reemite() {
        var first = openRequest(7);
        events.published.clear();
        timeline.clear();

        var second = openRequest(7);

        assertThat(second.created()).isFalse();
        assertThat(second.conversation().id()).isEqualTo(first.conversation().id());
        assertThat(conversations.store).hasSize(1);
        assertThat(events.published).isEmpty();
        assertThat(timeline).doesNotContain("conversation.created");
    }

    @Test
    void si_el_tiempo_real_falla_la_conversacion_igual_se_crea() {
        realtime.failing = true;
        assertThat(openRequest(7).created()).isTrue();
        assertThat(conversations.store).hasSize(1);
    }

    // --- ApplyRequestStatus (ADR-012, DP-02) ---

    @Test
    void un_estado_no_terminal_actualiza_el_estado_copiado_y_avisa_sin_cerrar() {
        openRequest(7);
        boolean changed = applyStatus.execute(new ApplyRequestStatusService.Command(7, "ACCEPTED", T0.plusSeconds(5)));

        Conversation c = conversations.findByRequestId(7).orElseThrow();
        assertThat(changed).isTrue();
        assertThat(c.contextStatus()).isEqualTo("ACCEPTED");
        assertThat(c.status()).isEqualTo(ConversationStatus.ACTIVE);
        assertThat(events.of(ChatEvents.CONVERSATION_READ_ONLY)).isEmpty();
        assertThat(realtime.recipientsByEvent).containsKey("conversation.status_changed");
    }

    @Test
    void un_estado_terminal_pasa_a_solo_lectura_y_publica_el_evento_una_sola_vez() {
        openRequest(7);
        applyStatus.execute(new ApplyRequestStatusService.Command(7, "CANCELLED", T0.plusSeconds(5)));
        applyStatus.execute(new ApplyRequestStatusService.Command(7, "CANCELLED", T0.plusSeconds(5))); // duplicado

        assertThat(conversations.findByRequestId(7).orElseThrow().status()).isEqualTo(ConversationStatus.READ_ONLY);
        var readOnly = events.of(ChatEvents.CONVERSATION_READ_ONLY);
        assertThat(readOnly).hasSize(1);
        assertThat(readOnly.get(0).data()).containsEntry("reason", "REQUEST_CANCELLED");
    }

    @Test
    void un_evento_fuera_de_orden_no_cambia_nada_ni_avisa() {
        openRequest(7);
        applyStatus.execute(new ApplyRequestStatusService.Command(7, "ACCEPTED", T0.plusSeconds(10)));
        timeline.clear();

        boolean changed = applyStatus.execute(new ApplyRequestStatusService.Command(7, "PENDING", T0.plusSeconds(3)));

        assertThat(changed).isFalse();
        assertThat(conversations.findByRequestId(7).orElseThrow().contextStatus()).isEqualTo("ACCEPTED");
        assertThat(timeline).doesNotContain("conversation.status_changed");
    }

    @Test
    void un_cambio_de_estado_de_una_solicitud_sin_conversacion_falla_para_que_el_consumidor_reintente() {
        assertThatThrownBy(() -> applyStatus.execute(
                new ApplyRequestStatusService.Command(404, "CANCELLED", T0))).extracting("code")
                .isEqualTo("CHAT_CONVERSATION_NOT_FOUND");
    }

    // --- GetConversation (RF-CHAT-04) ---

    @Test
    void el_detalle_incluye_no_leidos_del_que_consulta_y_solo_lo_ve_un_participante() {
        long id = openRequest(7).conversation().id();
        var get = new GetConversationService(conversations);

        var view = get.byId(CLIENT, id);
        assertThat(view.type()).isEqualTo(ConversationType.REQUEST);
        assertThat(view.participants()).hasSize(2);
        assertThat(get.byRequest(OFFERER, 7).id()).isEqualTo(id);
        assertThatThrownBy(() -> get.byId(STRANGER, id)).extracting("code").isEqualTo("CHAT_NOT_PARTICIPANT");
        assertThatThrownBy(() -> get.byRequest(STRANGER, 7)).extracting("code").isEqualTo("CHAT_NOT_PARTICIPANT");
        assertThatThrownBy(() -> get.byId(CLIENT, 999)).extracting("code").isEqualTo("CHAT_CONVERSATION_NOT_FOUND");
        assertThatThrownBy(() -> get.byRequest(CLIENT, 999)).extracting("code").isEqualTo("CHAT_CONVERSATION_NOT_FOUND");
    }

    // --- MarkAsRead (RF-CHAT-14) ---

    @Test
    void marcar_leido_avanza_el_puntero_y_avisa_a_los_participantes() {
        long id = openRequest(7).conversation().id();
        Conversation c = conversations.findById(id).orElseThrow();
        c.registerMessage(CLIENT, T0.plusSeconds(1));
        c.registerMessage(CLIENT, T0.plusSeconds(2));
        conversations.save(c);
        var read = new MarkAsReadService(conversations, realtime);

        Optional<Long> effective = read.execute(OFFERER, id, 2);

        assertThat(effective).contains(2L);
        assertThat(conversations.findById(id).orElseThrow().unreadFor(OFFERER)).isZero();
        assertThat(realtime.recipientsByEvent.get("conversation.read_updated")).containsExactlyInAnyOrder(CLIENT, OFFERER);
    }

    @Test
    void marcar_leido_dos_veces_hasta_el_mismo_punto_no_vuelve_a_avisar() {
        long id = openRequest(7).conversation().id();
        Conversation c = conversations.findById(id).orElseThrow();
        c.registerMessage(CLIENT, T0.plusSeconds(1));
        conversations.save(c);
        var read = new MarkAsReadService(conversations, realtime);
        read.execute(OFFERER, id, 1);
        timeline.clear();

        assertThat(read.execute(OFFERER, id, 1)).isEmpty();
        assertThat(timeline).isEmpty();
    }

    @Test
    void un_tercero_no_puede_marcar_leido() {
        long id = openRequest(7).conversation().id();
        var read = new MarkAsReadService(conversations, realtime);
        assertThatThrownBy(() -> read.execute(STRANGER, id, 1)).extracting("code").isEqualTo("CHAT_NOT_PARTICIPANT");
    }

    // --- ListMyConversations (RF-CHAT-03/31) ---

    private static ConversationSummary row(long id, Instant at) {
        return new ConversationSummary(id, ConversationType.REQUEST, ConversationStatus.ACTIVE, id, "PENDING", at, 0,
                OFFERER, "Beto", new ConversationSummary.LastMessage(CLIENT, MessageKind.TEXT, "hola"));
    }

    @Test
    void el_listado_pasa_los_filtros_combinados_y_multivaluados_al_modelo_de_lectura() {
        var readModel = new Fakes.CannedReadModel();
        var list = new ListMyConversationsService(readModel);
        var filter = new ConversationFilter(Set.of(ConversationStatus.ACTIVE, ConversationStatus.READ_ONLY),
                Set.of("PENDING", "ACCEPTED"), Set.of());

        list.execute(CLIENT, filter, null, null);

        assertThat(readModel.lastFilter).isEqualTo(filter);
    }

    @Test
    void el_listado_sin_filtro_no_filtra_nada() {
        var readModel = new Fakes.CannedReadModel();
        new ListMyConversationsService(readModel).execute(CLIENT, null, null, null);
        assertThat(readModel.lastFilter.statuses()).isEmpty();
        assertThat(readModel.lastFilter.requestStatuses()).isEmpty();
    }

    @Test
    void el_listado_pide_una_fila_de_mas_y_devuelve_cursor_solo_si_hay_pagina_siguiente() {
        var readModel = new Fakes.CannedReadModel();
        var list = new ListMyConversationsService(readModel);
        readModel.rows = List.of(row(3, T0.plusSeconds(3)), row(2, T0.plusSeconds(2)), row(1, T0.plusSeconds(1)));

        ConversationPage page = list.execute(CLIENT, null, null, 2);

        assertThat(readModel.lastLimit).isEqualTo(3);
        assertThat(page.items()).extracting(ConversationSummary::id).containsExactly(3L, 2L);
        assertThat(page.nextCursor()).isEqualTo(new ConversationCursor(T0.plusSeconds(2), 2));

        ConversationPage last = list.execute(CLIENT, null, page.nextCursor(), 5);
        assertThat(last.nextCursor()).isNull();
    }

    @Test
    void el_limite_del_listado_se_acota() {
        var readModel = new Fakes.CannedReadModel();
        var list = new ListMyConversationsService(readModel);
        list.execute(CLIENT, null, null, 10_000);
        assertThat(readModel.lastLimit).isEqualTo(ListMyConversationsService.MAX_LIMIT + 1);
        list.execute(CLIENT, null, null, 0);
        assertThat(readModel.lastLimit).isEqualTo(2);
    }
}
