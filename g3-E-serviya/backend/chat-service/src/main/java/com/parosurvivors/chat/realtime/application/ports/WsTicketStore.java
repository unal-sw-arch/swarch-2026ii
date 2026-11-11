package com.parosurvivors.chat.realtime.application.ports;

import java.util.Optional;

/** Tickets de un solo uso y vida corta para abrir el WebSocket (ADR-010). */
public interface WsTicketStore {

    /** @throws TicketStoreUnavailableException si no se puede guardar (Redis caído o lleno) → 503. */
    String issue(long userId);

    /** Consume el ticket una sola vez: un segundo uso (aunque sea concurrente) devuelve vacío. */
    Optional<Long> consume(String ticket);

    int ttlSeconds();
}
