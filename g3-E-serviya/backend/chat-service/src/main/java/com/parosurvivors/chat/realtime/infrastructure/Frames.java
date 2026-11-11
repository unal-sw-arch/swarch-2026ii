package com.parosurvivors.chat.realtime.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;

/** Sobre JSON del protocolo WebSocket (guía §8.2): {@code {"type", "id"?, "payload"}}. */
final class Frames {

    private Frames() {
    }

    static String of(ObjectMapper mapper, String type, String refId, Map<String, Object> payload) {
        Map<String, Object> frame = new LinkedHashMap<>();
        frame.put("type", type);
        if (refId != null) {
            frame.put("id", refId);
        }
        frame.put("payload", payload);
        try {
            return mapper.writeValueAsString(frame);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo serializar el frame " + type, e);
        }
    }
}
