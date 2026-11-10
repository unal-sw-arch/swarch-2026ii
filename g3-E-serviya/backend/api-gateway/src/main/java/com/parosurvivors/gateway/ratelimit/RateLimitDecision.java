package com.parosurvivors.gateway.ratelimit;

/** Resultado de consumir cuota: si se permite y, si no, cuántos segundos esperar (cabecera Retry-After). */
public record RateLimitDecision(boolean allowed, long retryAfterSeconds) {

    public static RateLimitDecision allow() {
        return new RateLimitDecision(true, 0L);
    }

    public static RateLimitDecision rejected(long retryAfterSeconds) {
        return new RateLimitDecision(false, retryAfterSeconds);
    }
}
