package com.parosurvivors.chat.shared.application.ports;

import java.util.Collection;
import java.util.Set;

/** Cuáles de estos usuarios tienen una conexión abierta en alguna instancia (para decidir si se notifica). */
public interface OnlineUsers {

    Set<Long> onlineAmong(Collection<Long> userIds);
}
