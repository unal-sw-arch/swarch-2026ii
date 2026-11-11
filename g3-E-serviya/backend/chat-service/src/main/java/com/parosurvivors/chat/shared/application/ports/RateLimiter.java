package com.parosurvivors.chat.shared.application.ports;

/** Límite de envío por usuario (RNF-CHAT-12). Implementación con fail-open ante caída de Redis. */
public interface RateLimiter {

    boolean tryAcquire(long userId);
}
