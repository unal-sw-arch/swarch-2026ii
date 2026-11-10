package com.parosurvivors.chat.conversations.application.dto;

import com.parosurvivors.chat.conversations.domain.ConversationStatus;
import com.parosurvivors.chat.conversations.domain.ConversationType;
import java.util.Set;

/** Filtros combinables y multivaluados (RF-CHAT-31). Un conjunto vacío significa «sin filtrar por eso». */
public record ConversationFilter(Set<ConversationStatus> statuses, Set<String> requestStatuses,
                                 Set<ConversationType> types) {

    public ConversationFilter {
        statuses = statuses == null ? Set.of() : Set.copyOf(statuses);
        requestStatuses = requestStatuses == null ? Set.of() : Set.copyOf(requestStatuses);
        types = types == null ? Set.of() : Set.copyOf(types);
    }

    public static ConversationFilter none() {
        return new ConversationFilter(Set.of(), Set.of(), Set.of());
    }
}
