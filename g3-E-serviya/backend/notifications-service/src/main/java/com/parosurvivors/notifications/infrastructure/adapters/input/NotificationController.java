package com.parosurvivors.notifications.infrastructure.adapters.input;

import com.parosurvivors.notifications.application.ports.input.NotificationChannelServicePort;
import com.parosurvivors.notifications.application.ports.input.NotificationDeliveryServicePort;
import com.parosurvivors.notifications.infrastructure.adapters.input.api.NotificationApi;
import com.parosurvivors.notifications.infrastructure.dto.response.NotificationChannelResponse;
import com.parosurvivors.notifications.infrastructure.dto.response.NotificationDeliveryResponse;
import com.parosurvivors.notifications.infrastructure.mappers.NotificationWebMapper;
import com.parosurvivors.notifications.infrastructure.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Adaptador de entrada (REST) de notificaciones. Mismas rutas que exponía el monolito: el API gateway las
 * enruta aquí y el frontend no cambia. La identidad sale SIEMPRE del JWT.
 */
@RestController
@RequiredArgsConstructor
public class NotificationController implements NotificationApi {

    private final NotificationDeliveryServicePort notificationDeliveryService;
    private final NotificationChannelServicePort notificationChannelService;
    private final NotificationWebMapper mapper;

    @Override
    @GetMapping("/api/v1/notifications")
    public ResponseEntity<Page<NotificationDeliveryResponse>> getDeliveries(
            @RequestParam(required = false) Boolean read,
            @RequestParam(required = false) Long channelId,
            @RequestParam(required = false) String status,
            Pageable pageable) {
        return ResponseEntity.ok(notificationDeliveryService
                .getDeliveries(CurrentUser.id(), read, channelId, status, pageable)
                .map(mapper::toResponse));
    }

    @Override
    @PostMapping("/api/v1/notifications/{id}/read")
    public ResponseEntity<Void> markAsRead(@PathVariable Long id) {
        notificationDeliveryService.markAsRead(id, CurrentUser.id());
        return ResponseEntity.noContent().build();
    }

    @Override
    @GetMapping("/api/v1/notification-channels")
    public ResponseEntity<List<NotificationChannelResponse>> getChannels() {
        return ResponseEntity.ok(mapper.toChannelResponses(notificationChannelService.getChannels()));
    }
}
