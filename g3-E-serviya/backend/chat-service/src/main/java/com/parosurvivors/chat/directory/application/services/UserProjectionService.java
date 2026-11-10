package com.parosurvivors.chat.directory.application.services;

import com.parosurvivors.chat.directory.application.ports.output.UserDirectory;
import com.parosurvivors.chat.directory.domain.DirectoryUser;
import com.parosurvivors.chat.directory.domain.UserStatus;
import java.time.Instant;

/**
 * Mantiene {@code user_directory} a partir de los eventos de usuarios. Los eventos pueden llegar repetidos o
 * fuera de orden: un evento más viejo que la fila no la modifica.
 */
public class UserProjectionService {

    /** Nombre mostrado tras eliminar la cuenta: se anonimiza el remitente, no se borran mensajes ajenos. */
    public static final String DELETED_USER_NAME = "Usuario eliminado";

    private final UserDirectory directory;

    public UserProjectionService(UserDirectory directory) {
        this.directory = directory;
    }

    /**
     * {@code users.user.registered} / {@code updated}: crea la fila o actualiza lo visible. {@code displayName} y
     * {@code avatarUrl} nulos significan «no cambió» (el evento updated trae solo los campos modificados); una
     * cadena vacía en {@code avatarUrl} borra el avatar.
     */
    public void upsertProfile(long userId, String displayName, String avatarUrl, Instant occurredAt) {
        DirectoryUser current = directory.find(userId).orElse(null);
        if (isStale(current, occurredAt)) {
            return;
        }
        if (current != null && current.status() == UserStatus.DELETED) {
            return; // una cuenta eliminada no vuelve a mostrar nombre
        }
        UserStatus status = current == null ? UserStatus.ACTIVE : current.status();
        String name = displayName != null && !displayName.isBlank() ? displayName
                : current != null ? current.displayName() : "Usuario " + userId;
        String avatar = avatarUrl == null ? (current == null ? null : current.avatarUrl())
                : avatarUrl.isBlank() ? null : avatarUrl;
        directory.save(new DirectoryUser(userId, name, avatar, status, occurredAt));
    }

    public void suspend(long userId, Instant occurredAt) {
        changeStatus(userId, UserStatus.SUSPENDED, occurredAt);
    }

    public void reactivate(long userId, Instant occurredAt) {
        changeStatus(userId, UserStatus.ACTIVE, occurredAt);
    }

    public void delete(long userId, Instant occurredAt) {
        DirectoryUser current = directory.find(userId).orElse(null);
        if (isStale(current, occurredAt)) {
            return;
        }
        directory.save(new DirectoryUser(userId, DELETED_USER_NAME, null, UserStatus.DELETED, occurredAt));
    }

    private void changeStatus(long userId, UserStatus status, Instant occurredAt) {
        DirectoryUser current = directory.find(userId).orElse(null);
        if (isStale(current, occurredAt) || (current != null && current.status() == UserStatus.DELETED)) {
            return;
        }
        // Si el evento de estado llega antes que el de registro, se crea una fila provisional.
        String name = current == null ? "Usuario " + userId : current.displayName();
        String avatar = current == null ? null : current.avatarUrl();
        directory.save(new DirectoryUser(userId, name, avatar, status, occurredAt));
    }

    private static boolean isStale(DirectoryUser current, Instant occurredAt) {
        return current != null && occurredAt.isBefore(current.updatedAt());
    }
}
