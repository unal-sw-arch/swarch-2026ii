package com.parosurvivors.chat.conversations.application.ports.output;

import com.parosurvivors.chat.conversations.application.dto.ConversationCursor;
import com.parosurvivors.chat.conversations.application.dto.ConversationFilter;
import com.parosurvivors.chat.conversations.application.dto.ConversationSummary;
import java.util.List;

/** Consulta de lectura optimizada del listado (§7.4): último mensaje, no leídos y filtros en una sola consulta. */
public interface ConversationReadModel {

    /** Ordenado por (last_message_at DESC, id DESC), sin las archivadas por el usuario, y hasta {@code limit} filas. */
    List<ConversationSummary> findForUser(long userId, ConversationFilter filter, ConversationCursor after, int limit);
}
