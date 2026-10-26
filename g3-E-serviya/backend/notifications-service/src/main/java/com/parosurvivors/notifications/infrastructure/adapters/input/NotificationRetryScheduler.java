package com.parosurvivors.notifications.infrastructure.adapters.input;

import com.parosurvivors.notifications.application.ports.input.NotificationDeliveryServicePort;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Adaptador de entrada por TIEMPO: reintenta periódicamente las entregas de notificación que fallaron
 * y aún no agotaron el máximo de intentos ({@code serviya.notifications.max-delivery-attempts}). Solo
 * enruta hacia el puerto de entrada. Cron por defecto: cada 15 minutos.
 *
 * <p>Sin lock distribuido: con ≥2 réplicas todas disparan, pero el reintento RECLAMA las filas con
 * {@code SELECT … FOR UPDATE SKIP LOCKED}, así que cada entrega la procesa una sola instancia.</p>
 */
@Component
@RequiredArgsConstructor
public class NotificationRetryScheduler {

    private final NotificationDeliveryServicePort notificationDeliveryService;

    @Scheduled(cron = "${serviya.notifications.cron.retry-failed:0 */15 * * * *}")
    public void retryFailedDeliveries() {
        notificationDeliveryService.retryFailedDeliveries();
    }
}
