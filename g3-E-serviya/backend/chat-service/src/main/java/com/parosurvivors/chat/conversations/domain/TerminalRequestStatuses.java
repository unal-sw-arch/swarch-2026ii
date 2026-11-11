package com.parosurvivors.chat.conversations.domain;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Estados de la solicitud que pasan la conversación a solo lectura (DP-11). El chat guarda el estado como
 * texto sin interpretarlo, salvo esta lista, que es configurable.
 */
public record TerminalRequestStatuses(Set<String> values) {

    public TerminalRequestStatuses {
        values = values.stream().map(String::trim).filter(s -> !s.isEmpty())
                .map(String::toUpperCase).collect(Collectors.toUnmodifiableSet());
    }

    public static TerminalRequestStatuses of(String... statuses) {
        return new TerminalRequestStatuses(Set.of(statuses));
    }

    public boolean isTerminal(String status) {
        return status != null && values.contains(status.toUpperCase());
    }
}
