package com.parosurvivors.chat.messaging.application.services;

import com.parosurvivors.chat.shared.domain.DomainException;

public class InvalidPaginationException extends DomainException {

    public InvalidPaginationException() {
        super("CHAT_INVALID_PAGINATION", "Usa before_seq o after_seq, no ambos");
    }
}
