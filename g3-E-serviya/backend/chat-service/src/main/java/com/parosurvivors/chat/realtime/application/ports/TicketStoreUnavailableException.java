package com.parosurvivors.chat.realtime.application.ports;

import com.parosurvivors.chat.shared.domain.DomainException;

public class TicketStoreUnavailableException extends DomainException {

    public TicketStoreUnavailableException(Throwable cause) {
        super("CHAT_TICKETS_UNAVAILABLE", "No se pueden emitir tickets en este momento; reintenta o usa REST");
        initCause(cause);
    }
}
