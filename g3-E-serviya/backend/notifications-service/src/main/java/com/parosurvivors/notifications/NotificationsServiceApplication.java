package com.parosurvivors.notifications;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Microservicio de notificaciones de ServiYa. Primer servicio extraido del monolito (Strangler Fig,
 * ESTADO-ACTUAL-Y-PLAN-FUTURO.md §7.4 paso 1). Dueño de las tablas notifications / notification_channels /
 * notification_deliveries en su PROPIA base de datos.
 *
 * <p>Entradas: (1) comandos {@code notification.requested} desde RabbitMQ (los publica el outbox del
 * monolito), (2) API REST de la bandeja del usuario detras del API gateway, (3) reintento programado.
 * Salida externa: Brevo (correo).</p>
 */
@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
public class NotificationsServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationsServiceApplication.class, args);
    }
}
