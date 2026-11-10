package com.parosurvivors.chat.conversations.infrastructure.adapters.input;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.parosurvivors.chat.conversations.application.services.ApplyRequestStatusService;
import com.parosurvivors.chat.conversations.application.services.OpenRequestConversationService;
import com.parosurvivors.chat.shared.infrastructure.messaging.ChatMessagingConfig;
import com.parosurvivors.chat.shared.infrastructure.messaging.EventEnvelope;
import com.parosurvivors.chat.shared.infrastructure.messaging.ProcessedEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Adaptador de entrada desde el bus: eventos del ciclo de vida de la solicitud (guía §9.2).
 * <ul>
 *   <li>{@code created} → abre la conversación (DP-01). Idempotente por solicitud.</li>
 *   <li>{@code status_changed} → actualiza el estado copiado y, si es terminal, solo lectura definitiva (DP-02).</li>
 * </ul>
 * Fallo permanente (JSON/campos inválidos) → DLQ sin reintentos. Fallo transitorio (BD caída, o un
 * {@code status_changed} que llegó antes que {@code created}) → la excepción se propaga y el contenedor reintenta con
 * backoff; agotados los intentos, DLQ.
 */
@Component
class RequestEventsListener {

    private static final Logger log = LoggerFactory.getLogger(RequestEventsListener.class);

    private final OpenRequestConversationService open;
    private final ApplyRequestStatusService applyStatus;
    private final ProcessedEvents processed;
    private final ObjectMapper mapper;

    RequestEventsListener(OpenRequestConversationService open, ApplyRequestStatusService applyStatus,
                          ProcessedEvents processed, ObjectMapper mapper) {
        this.open = open;
        this.applyStatus = applyStatus;
        this.processed = processed;
        this.mapper = mapper;
    }

    @RabbitListener(queues = ChatMessagingConfig.REQUESTS_QUEUE)
    void onMessage(Message amqp) {
        EventEnvelope event = EventEnvelope.parse(mapper, amqp.getBody());
        EventEnvelope.bindTracing(event);
        try {
            if (processed.alreadyProcessed(event.eventId())) {
                log.info("Evento {} ya procesado: se ignora", event.eventId());
                return;
            }
            switch (event.eventType()) {
                case ChatMessagingConfig.REQUEST_CREATED -> open.execute(new OpenRequestConversationService.Command(
                        event.requiredLong("request_id"), event.requiredLong("client_id"),
                        event.requiredLong("offerer_id"), event.requiredText("status"), event.occurredAt()));
                case ChatMessagingConfig.REQUEST_STATUS_CHANGED -> applyStatus.execute(
                        new ApplyRequestStatusService.Command(event.requiredLong("request_id"),
                                event.requiredText("status"), event.occurredAt()));
                default -> {
                    // Tipo nuevo del productor que el chat no usa: se confirma y se ignora (contrato §4).
                    log.debug("Evento {} de tipo {} ignorado", event.eventId(), event.eventType());
                    return;
                }
            }
            processed.markProcessed(event.eventId(), event.eventType());
        } finally {
            EventEnvelope.clearTracing();
        }
    }
}
