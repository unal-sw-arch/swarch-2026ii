package com.parosurvivors.notifications.infrastructure.adapters.input.api;

import com.parosurvivors.notifications.infrastructure.dto.response.NotificationChannelResponse;
import com.parosurvivors.notifications.infrastructure.dto.response.NotificationDeliveryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

import java.util.List;

/**
 * Documentacion OpenAPI/Swagger de las notificaciones del usuario. notify/deliver NO se exponen por HTTP:
 * llegan como mensajes del bus. Convencion: docs aqui; binding y @Parameter en el controller.
 */
@Tag(name = "Notificaciones", description = "Bandeja de notificaciones del usuario y canales disponibles")
@SecurityRequirement(name = "bearerAuth")
public interface NotificationApi {

    @Operation(summary = "Listar las notificaciones propias con filtros",
            description = "Filtros: read, channelId, status. RF-061, RF-062, RF-082..RF-089.")
    @ApiResponse(responseCode = "200", description = "Pagina de notificaciones")
    ResponseEntity<Page<NotificationDeliveryResponse>> getDeliveries(
            Boolean read, Long channelId, String status, Pageable pageable);

    @Operation(summary = "Marcar una notificacion como leida")
    @ApiResponse(responseCode = "204", description = "Notificacion marcada como leida")
    @ApiResponse(responseCode = "404", description = "No existe o no es del usuario autenticado")
    ResponseEntity<Void> markAsRead(Long id);

    @Operation(summary = "Listar los canales de notificacion disponibles")
    @ApiResponse(responseCode = "200", description = "Canales disponibles")
    ResponseEntity<List<NotificationChannelResponse>> getChannels();
}
