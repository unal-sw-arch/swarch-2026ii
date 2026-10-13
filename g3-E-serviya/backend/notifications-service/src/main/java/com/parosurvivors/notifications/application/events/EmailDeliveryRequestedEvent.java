package com.parosurvivors.notifications.application.events;

import java.util.Map;

/**
 * Evento interno del servicio: una entrega EMAIL quedó PENDING y debe enviarse.
 * Se publica dentro de la transacción que registra la notificación y lo consume un listener AFTER_COMMIT
 * que envía el correo en una transacción NUEVA, de modo que la llamada al proveedor externo NO bloquee ni
 * pueda revertir el registro (ni provoque el reproceso del mensaje del bus).
 *
 * <p>{@code protectedData} viaja en memoria en el evento (no se persiste), así el envío inicial
 * conserva las variables de plantilla. Los reintentos programados sí van sin ese mapa.</p>
 */
public record EmailDeliveryRequestedEvent(Long deliveryId, Map<String, String> protectedData) {
}
