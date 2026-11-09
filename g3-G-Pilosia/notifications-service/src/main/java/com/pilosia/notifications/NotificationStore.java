package com.pilosia.notifications;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

// Guarda las notificaciones en Redis: una lista por docente (clave "notifications:professor:<id>")
@Component
public class NotificationStore {
    private static final int MAX_PER_PROFESSOR = 50;

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public NotificationStore(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    public void add(Map<String, String> notification) {
        String key = keyFor(notification.get("professorId"));
        try {
            redis.opsForList().rightPush(key, objectMapper.writeValueAsString(notification));
            // solo se guardan las últimas notificaciones de cada docente
            redis.opsForList().trim(key, -MAX_PER_PROFESSOR, -1);
        } catch (Exception error) {
            throw new IllegalStateException("No se pudo guardar la notificación", error);
        }
    }

    public List<Map<String, String>> forProfessor(String professorId) {
        List<String> saved = redis.opsForList().range(keyFor(professorId), 0, -1);
        List<Map<String, String>> result = new ArrayList<>();
        if (saved == null) return result;
        for (String json : saved) {
            try {
                result.add(objectMapper.readValue(json, new TypeReference<>() {}));
            } catch (Exception error) {
                // si un registro está dañado se ignora
            }
        }
        return result;
    }

    private String keyFor(String professorId) {
        return "notifications:professor:" + professorId;
    }
}
