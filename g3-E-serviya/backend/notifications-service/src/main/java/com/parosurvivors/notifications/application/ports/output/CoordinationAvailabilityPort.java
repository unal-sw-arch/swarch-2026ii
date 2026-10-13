package com.parosurvivors.notifications.application.ports.output;

/**
 * Señal de si el sustrato de coordinación ({@code redis-coord}) está disponible. Mientras Redis está caído
 * el rate limiting del monolito no protege (políticas OPEN), así que el envío de notificaciones EXTERNAS se
 * difiere para no amplificar un posible abuso (protocolo D-07/1a). Se trajo del kernel {@code shared} del
 * monolito, del que era el único consumidor.
 */
public interface CoordinationAvailabilityPort {

    /** {@code true} si {@code redis-coord} respondió al último sondeo (cacheado unos segundos). */
    boolean isAvailable();
}
