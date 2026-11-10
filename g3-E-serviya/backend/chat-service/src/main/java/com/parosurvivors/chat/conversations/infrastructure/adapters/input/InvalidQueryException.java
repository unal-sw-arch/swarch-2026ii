package com.parosurvivors.chat.conversations.infrastructure.adapters.input;

import com.parosurvivors.chat.shared.domain.DomainException;

class InvalidQueryException extends DomainException {

    InvalidQueryException(String code, String message) {
        super(code, message);
    }
}
