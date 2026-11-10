package com.parosurvivors.chat.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.parosurvivors.chat.conversations.application.ports.output.ConversationRepository;
import com.parosurvivors.chat.conversations.application.services.OpenRequestConversationService;
import com.parosurvivors.chat.conversations.domain.Conversation;
import com.parosurvivors.chat.conversations.domain.TerminalRequestStatuses;
import com.parosurvivors.chat.directory.application.ports.output.UserDirectory;
import com.parosurvivors.chat.messaging.application.dto.SendMessageResult;
import com.parosurvivors.chat.messaging.application.ports.output.MessageRepository;
import com.parosurvivors.chat.messaging.application.services.SendMessageService;
import com.parosurvivors.chat.shared.application.ports.EventPublisher;
import com.parosurvivors.chat.shared.application.ports.NotificationRequester;
import com.parosurvivors.chat.shared.application.ports.OnlineUsers;
import com.parosurvivors.chat.shared.application.ports.RateLimiter;
import com.parosurvivors.chat.shared.application.ports.RealtimeNotifier;
import com.parosurvivors.chat.shared.application.ports.UnitOfWork;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Los casos de uso REALES contra MySQL REAL, con hilos simultáneos. Es lo que ningún fake puede probar:
 * que el SELECT ... FOR UPDATE y los índices únicos dan orden total sin huecos e idempotencia (RNF-CHAT-06/07).
 */
class ConcurrencyIntegrationTest extends IntegrationTestBase {

    private static final long CLIENT = 10;
    private static final long OFFERER = 20;
    private static final Instant T0 = Instant.parse("2026-11-09T12:00:00Z");
    private static final TerminalRequestStatuses TERMINAL = TerminalRequestStatuses.of("CANCELLED");

    @Autowired ConversationRepository conversations;
    @Autowired MessageRepository messages;
    @Autowired UserDirectory users;
    @Autowired EventPublisher events;
    @Autowired UnitOfWork uow;

    private SendMessageService sendService() {
        RateLimiter allow = mock(RateLimiter.class);
        when(allow.tryAcquire(anyLong())).thenReturn(true);
        return new SendMessageService(conversations, messages, users, events, mock(NotificationRequester.class),
                mock(RealtimeNotifier.class), mock(OnlineUsers.class), allow, uow, Clock.systemUTC(), 2000);
    }

    private static <T> List<T> runConcurrently(int threads, java.util.function.IntFunction<Callable<T>> taskFor) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<T>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            Callable<T> task = taskFor.apply(i);
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                return task.call();
            }));
        }
        ready.await();
        go.countDown();
        List<T> results = new ArrayList<>();
        for (Future<T> f : futures) {
            results.add(f.get(60, TimeUnit.SECONDS));
        }
        pool.shutdown();
        return results;
    }

    private long newConversation() {
        return conversations.save(Conversation.openForRequest(7, CLIENT, OFFERER, "PENDING", T0, TERMINAL)).id();
    }

    @Test
    void n_envios_simultaneos_a_la_misma_conversacion_dan_seq_1_a_n_sin_huecos_ni_duplicados() throws Exception {
        long id = newConversation();
        SendMessageService service = sendService();
        int n = 30;

        List<SendMessageResult> results = runConcurrently(n, i -> () -> service.execute(
                new SendMessageService.Command(i % 2 == 0 ? CLIENT : OFFERER, id, "cid-" + i, "mensaje " + i)));

        assertThat(results).extracting(r -> r.message().seq()).doesNotHaveDuplicates()
                .containsExactlyInAnyOrder(java.util.stream.LongStream.rangeClosed(1, n).boxed().toArray(Long[]::new));
        assertThat(jdbc.queryForList("SELECT seq FROM messages WHERE conversation_id = ? ORDER BY seq", Long.class, id))
                .containsExactlyElementsOf(java.util.stream.LongStream.rangeClosed(1, n).boxed().toList());
        assertThat(conversations.findById(id).orElseThrow().lastSeq()).isEqualTo(n);
        // Un evento chat.message.sent por mensaje, escritos en la misma transacción.
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM outbox_events WHERE routing_key = 'chat.message.sent'",
                Integer.class)).isEqualTo(n);
    }

    @Test
    void el_mismo_reenvio_simultaneo_se_guarda_una_sola_vez_y_todos_reciben_el_mismo_mensaje() throws Exception {
        long id = newConversation();
        SendMessageService service = sendService();

        List<SendMessageResult> results = runConcurrently(12, i -> () -> service.execute(
                new SendMessageService.Command(CLIENT, id, "mismo-id", "Hola")));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM messages WHERE conversation_id = ?", Integer.class, id))
                .isEqualTo(1);
        assertThat(results).extracting(r -> r.message().id()).containsOnly(results.get(0).message().id());
        assertThat(results).filteredOn(r -> !r.duplicate()).hasSize(1);          // solo uno lo creó
        assertThat(conversations.findById(id).orElseThrow().lastSeq()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM outbox_events", Integer.class)).isEqualTo(1);
    }

    @Test
    void envios_a_conversaciones_distintas_no_se_bloquean_y_cada_una_lleva_su_propia_secuencia() throws Exception {
        long a = newConversation();
        long b = conversations.save(Conversation.openForRequest(8, CLIENT, 21, "PENDING", T0, TERMINAL)).id();
        SendMessageService service = sendService();

        runConcurrently(20, i -> () -> service.execute(
                new SendMessageService.Command(CLIENT, i % 2 == 0 ? a : b, "cid-" + i, "m" + i)));

        assertThat(conversations.findById(a).orElseThrow().lastSeq()).isEqualTo(10);
        assertThat(conversations.findById(b).orElseThrow().lastSeq()).isEqualTo(10);
    }

    @Test
    void abrir_a_la_vez_la_conversacion_de_la_misma_solicitud_crea_una_sola_y_un_solo_evento() throws Exception {
        OpenRequestConversationService open = new OpenRequestConversationService(conversations, events,
                mock(RealtimeNotifier.class), uow, TERMINAL);

        List<OpenRequestConversationService.Result> results = runConcurrently(10, i -> () -> open.execute(
                new OpenRequestConversationService.Command(55, CLIENT, OFFERER, "PENDING", T0)));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM conversations WHERE context_id = 55", Integer.class))
                .isEqualTo(1);
        assertThat(results).extracting(r -> r.conversation().id()).containsOnly(results.get(0).conversation().id());
        assertThat(results).filteredOn(OpenRequestConversationService.Result::created).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM outbox_events WHERE routing_key = 'chat.conversation.created'",
                Integer.class)).isEqualTo(1);
    }
}
