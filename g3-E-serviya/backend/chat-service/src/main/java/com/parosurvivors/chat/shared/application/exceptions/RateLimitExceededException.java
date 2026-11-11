package com.parosurvivors.chat.shared.application.exceptions;

import com.parosurvivors.chat.shared.domain.DomainException;

public class RateLimitExceededException extends DomainException {

    public RateLimitExceededException() {
        super("CHAT_RATE_LIMITED", "Estás enviando mensajes demasiado rápido");
    }
}
