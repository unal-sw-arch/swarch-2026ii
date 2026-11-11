package com.parosurvivors.chat.shared.domain;

/** Error de regla de negocio. {@code code} es estable y lo expone la API (p. ej. CHAT_CONVERSATION_READ_ONLY). */
public abstract class DomainException extends RuntimeException {

    private final String code;

    protected DomainException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
