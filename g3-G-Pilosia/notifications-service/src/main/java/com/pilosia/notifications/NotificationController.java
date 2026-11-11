package com.pilosia.notifications;

import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class NotificationController {
    private final NotificationStore store;

    public NotificationController(NotificationStore store) {
        this.store = store;
    }

    @GetMapping("/notifications")
    public List<Map<String, String>> notifications(@RequestParam String professorId) {
        return store.forProfessor(professorId);
    }
}
