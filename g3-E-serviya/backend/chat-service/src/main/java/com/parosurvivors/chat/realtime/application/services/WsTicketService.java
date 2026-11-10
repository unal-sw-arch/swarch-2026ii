package com.parosurvivors.chat.realtime.application.services;

import com.parosurvivors.chat.realtime.application.ports.WsTicketStore;
import java.util.Optional;

public class WsTicketService {

    private final WsTicketStore store;

    public WsTicketService(WsTicketStore store) {
        this.store = store;
    }

    public record IssuedTicket(String ticket, int expiresInSeconds) {
    }

    public IssuedTicket issue(long userId) {
        return new IssuedTicket(store.issue(userId), store.ttlSeconds());
    }

    /** Devuelve el usuario dueño del ticket, o vacío si es inválido, expiró o ya se usó. */
    public Optional<Long> consume(String ticket) {
        if (ticket == null || ticket.isBlank() || ticket.length() > 128) {
            return Optional.empty();
        }
        return store.consume(ticket);
    }
}
