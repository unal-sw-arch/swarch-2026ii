package com.parosurvivors.chat.conversations.application.services;

import com.parosurvivors.chat.conversations.application.dto.ConversationCursor;
import com.parosurvivors.chat.conversations.application.dto.ConversationFilter;
import com.parosurvivors.chat.conversations.application.dto.ConversationPage;
import com.parosurvivors.chat.conversations.application.dto.ConversationSummary;
import com.parosurvivors.chat.conversations.application.ports.output.ConversationReadModel;
import java.util.List;

/** RF-CHAT-03 y 31: listado por actividad reciente con último mensaje, no leídos y filtros combinables. */
public class ListMyConversationsService {

    public static final int DEFAULT_LIMIT = 20;
    public static final int MAX_LIMIT = 50;

    private final ConversationReadModel readModel;

    public ListMyConversationsService(ConversationReadModel readModel) {
        this.readModel = readModel;
    }

    public ConversationPage execute(long userId, ConversationFilter filter, ConversationCursor cursor, Integer limit) {
        int size = limit == null ? DEFAULT_LIMIT : Math.max(1, Math.min(limit, MAX_LIMIT));
        // Se pide una fila de más para saber si hay página siguiente sin una consulta extra.
        List<ConversationSummary> rows = readModel.findForUser(userId, filter == null ? ConversationFilter.none() : filter,
                cursor, size + 1);
        if (rows.size() <= size) {
            return new ConversationPage(rows, null);
        }
        List<ConversationSummary> page = rows.subList(0, size);
        ConversationSummary last = page.get(size - 1);
        return new ConversationPage(List.copyOf(page), new ConversationCursor(last.lastMessageAt(), last.id()));
    }
}
