package com.parosurvivors.chat.conversations.infrastructure.adapters.output;

import com.parosurvivors.chat.conversations.application.dto.ConversationCursor;
import com.parosurvivors.chat.conversations.application.dto.ConversationFilter;
import com.parosurvivors.chat.conversations.application.dto.ConversationSummary;
import com.parosurvivors.chat.conversations.application.ports.output.ConversationReadModel;
import com.parosurvivors.chat.conversations.domain.ConversationStatus;
import com.parosurvivors.chat.conversations.domain.ConversationType;
import com.parosurvivors.chat.messaging.domain.MessageKind;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Listado de «mis conversaciones» (§7.4): una sola consulta con último mensaje, no leídos, contraparte y filtros.
 * Los filtros multivaluados son {@code IN (:param)} con valores enlazados (nunca texto interpolado); cada cláusula
 * se agrega SOLO si hay filtro, porque MySQL no tiene arrays.
 */
@Component
class ConversationReadModelAdapter implements ConversationReadModel {

    private static final int PREVIEW_CHARS = 200;

    private final NamedParameterJdbcTemplate jdbc;

    ConversationReadModelAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ConversationSummary> findForUser(long userId, ConversationFilter filter, ConversationCursor after,
                                                 int limit) {
        StringBuilder sql = new StringBuilder("""
                SELECT c.id, c.type, c.status, c.context_id, c.context_status, c.last_message_at,
                       c.last_seq - p.last_read_seq AS unread_count,
                       cp.user_id AS counterpart_id,
                       COALESCE(u.display_name, CONCAT('Usuario ', cp.user_id)) AS counterpart_name,
                       m.sender_id AS last_sender_id, m.kind AS last_kind, LEFT(m.body, :preview) AS last_body
                  FROM conversation_participants p
                  JOIN conversations c ON c.id = p.conversation_id
                  LEFT JOIN conversation_participants cp ON cp.conversation_id = c.id AND cp.user_id <> p.user_id
                  LEFT JOIN user_directory u ON u.user_id = cp.user_id
                  LEFT JOIN messages m ON m.conversation_id = c.id AND m.seq = c.last_seq
                 WHERE p.user_id = :userId
                   AND p.archived_at IS NULL
                """);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userId", userId).addValue("preview", PREVIEW_CHARS).addValue("limit", limit);

        if (!filter.statuses().isEmpty()) {
            sql.append("   AND c.status IN (:statuses)\n");
            params.addValue("statuses", filter.statuses().stream().map(ConversationStatus::name).toList());
        }
        if (!filter.requestStatuses().isEmpty()) {
            sql.append("   AND c.context_status IN (:requestStatuses)\n");
            params.addValue("requestStatuses", List.copyOf(filter.requestStatuses()));
        }
        if (!filter.types().isEmpty()) {
            sql.append("   AND c.type IN (:types)\n");
            params.addValue("types", filter.types().stream().map(ConversationType::name).toList());
        }
        if (after != null) {
            sql.append("   AND (c.last_message_at, c.id) < (:afterTs, :afterId)\n");
            // LocalDateTime (no Timestamp): Timestamp.valueOf reinterpretaría la hora en la zona de la JVM y desplazaría el cursor.
            params.addValue("afterTs", LocalDateTime.ofInstant(after.lastMessageAt(), ZoneOffset.UTC))
                    .addValue("afterId", after.id());
        }
        sql.append(" ORDER BY c.last_message_at DESC, c.id DESC LIMIT :limit");

        return jdbc.query(sql.toString(), params, (rs, i) -> {
            String body = rs.getString("last_body");
            long senderId = rs.getLong("last_sender_id");
            Long sender = rs.wasNull() ? null : senderId;
            ConversationSummary.LastMessage last = body == null ? null
                    : new ConversationSummary.LastMessage(sender, MessageKind.valueOf(rs.getString("last_kind")), body);
            long contextId = rs.getLong("context_id");
            return new ConversationSummary(rs.getLong("id"), ConversationType.valueOf(rs.getString("type")),
                    ConversationStatus.valueOf(rs.getString("status")), rs.wasNull() ? null : contextId,
                    rs.getString("context_status"),
                    rs.getObject("last_message_at", LocalDateTime.class).toInstant(ZoneOffset.UTC),
                    rs.getLong("unread_count"), rs.getLong("counterpart_id"), rs.getString("counterpart_name"), last);
        });
    }
}
