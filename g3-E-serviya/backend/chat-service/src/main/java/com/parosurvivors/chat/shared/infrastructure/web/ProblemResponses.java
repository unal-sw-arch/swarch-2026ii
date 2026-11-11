package com.parosurvivors.chat.shared.infrastructure.web;

import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/** Errores REST en formato RFC 9457 (Problem Details), con el {@code code} estable que usa el frontend. */
public final class ProblemResponses {

    private ProblemResponses() {
    }

    public static ResponseEntity<Map<String, Object>> problem(HttpStatus status, String code, String title) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "https://serviya/errors/" + code.toLowerCase().replace('_', '-'));
        body.put("title", title);
        body.put("status", status.value());
        body.put("code", code);
        body.put("trace_id", MDC.get(CorrelationIdFilter.MDC_KEY));
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(body);
    }

    /** Código de error de dominio → estado HTTP (guía §8.1). */
    public static HttpStatus statusFor(String code) {
        return switch (code) {
            case "CHAT_NOT_PARTICIPANT", "CHAT_USER_SUSPENDED" -> HttpStatus.FORBIDDEN;
            case "CHAT_CONVERSATION_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "CHAT_CONVERSATION_READ_ONLY" -> HttpStatus.CONFLICT;
            case "CHAT_RATE_LIMITED" -> HttpStatus.TOO_MANY_REQUESTS;
            case "CHAT_TICKETS_UNAVAILABLE" -> HttpStatus.SERVICE_UNAVAILABLE;
            default -> HttpStatus.BAD_REQUEST; // validaciones: mensaje vacío/largo, paginación, filtros, cursor…
        };
    }
}
