package com.pilosia.notifications;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Component;

@Component
public class NotificationStore {
    private final CopyOnWriteArrayList<Map<String, String>> notifications = new CopyOnWriteArrayList<>();

    public void add(Map<String, String> notification) {
        notifications.add(notification);
    }

    public List<Map<String, String>> forProfessor(String professorId) {
        return new ArrayList<>(notifications.stream()
            .filter(notification -> professorId.equals(notification.get("professorId")))
            .toList());
    }
}
