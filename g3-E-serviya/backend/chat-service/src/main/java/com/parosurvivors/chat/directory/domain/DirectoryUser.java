package com.parosurvivors.chat.directory.domain;

import java.time.Instant;

/** Proyección local de un usuario: solo lo necesario para autorizar y mostrar (ADR-006). */
public record DirectoryUser(long userId, String displayName, String avatarUrl, UserStatus status, Instant updatedAt) {

    public boolean canSendMessages() {
        return status == UserStatus.ACTIVE;
    }
}
