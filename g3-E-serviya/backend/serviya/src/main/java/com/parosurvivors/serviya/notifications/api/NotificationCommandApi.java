package com.parosurvivors.serviya.notifications.api;

/**
 * API publica del modulo notifications para que otros modulos soliciten el envio de una notificacion.
 */
public interface NotificationCommandApi {

    void notify(NotificationCommand command);
}
