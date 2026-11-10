package com.parosurvivors.chat.conversations.infrastructure.adapters.input;

import com.parosurvivors.chat.conversations.application.dto.ConversationCursor;
import com.parosurvivors.chat.conversations.application.dto.ConversationFilter;
import com.parosurvivors.chat.conversations.application.dto.ConversationPage;
import com.parosurvivors.chat.conversations.application.services.GetConversationService;
import com.parosurvivors.chat.conversations.application.services.ListMyConversationsService;
import com.parosurvivors.chat.conversations.application.services.MarkAsReadService;
import com.parosurvivors.chat.conversations.domain.ConversationStatus;
import com.parosurvivors.chat.conversations.domain.ConversationType;
import com.parosurvivors.chat.conversations.infrastructure.dto.response.ConversationDetailResponse;
import com.parosurvivors.chat.conversations.infrastructure.dto.response.ConversationPageResponse;
import com.parosurvivors.chat.conversations.infrastructure.dto.response.ConversationSummaryResponse;
import com.parosurvivors.chat.shared.infrastructure.security.CurrentUser;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat/v1/conversations")
class ConversationController {

    private final ListMyConversationsService list;
    private final GetConversationService get;
    private final MarkAsReadService markAsRead;

    ConversationController(ListMyConversationsService list, GetConversationService get, MarkAsReadService markAsRead) {
        this.list = list;
        this.get = get;
        this.markAsRead = markAsRead;
    }

    /**
     * RF-CHAT-03 y 31. {@code status}, {@code request_status} y {@code type} aceptan varios valores separados por
     * coma y se combinan entre sí.
     */
    @GetMapping
    ConversationPageResponse list(@AuthenticationPrincipal Jwt jwt,
                                  @RequestParam(required = false) List<String> status,
                                  @RequestParam(name = "request_status", required = false) List<String> requestStatus,
                                  @RequestParam(required = false) List<String> type,
                                  @RequestParam(required = false) String cursor,
                                  @RequestParam(required = false) Integer limit) {
        ConversationFilter filter = new ConversationFilter(
                enums(status, ConversationStatus.class, "status"),
                requestStatus == null ? Set.of() : trimmedUpper(requestStatus),
                enums(type, ConversationType.class, "type"));
        ConversationPage page = list.execute(CurrentUser.id(jwt), filter, decode(cursor), limit);
        return new ConversationPageResponse(page.items().stream().map(ConversationSummaryResponse::of).toList(),
                encode(page.nextCursor()));
    }

    @GetMapping("/{id}")
    ConversationDetailResponse detail(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        return ConversationDetailResponse.of(get.byId(CurrentUser.id(jwt), id));
    }

    @GetMapping("/by-request/{requestId}")
    ConversationDetailResponse byRequest(@AuthenticationPrincipal Jwt jwt, @PathVariable long requestId) {
        return ConversationDetailResponse.of(get.byRequest(CurrentUser.id(jwt), requestId));
    }

    record ReadForm(Long seq) {
    }

    /** {@code seq} es el efectivo (acotado al último mensaje) o null si no hubo avance. */
    @PostMapping("/{id}/read")
    Map<String, Object> read(@AuthenticationPrincipal Jwt jwt, @PathVariable long id, @RequestBody ReadForm form) {
        if (form.seq() == null || form.seq() < 0) {
            throw new InvalidQueryException("CHAT_INVALID_SEQ", "seq es obligatorio y no puede ser negativo");
        }
        var effective = markAsRead.execute(CurrentUser.id(jwt), id, form.seq());
        return Map.of("updated", effective.isPresent(), "seq", effective.orElse(form.seq()));
    }

    // --- parseo de parámetros ---

    private static <E extends Enum<E>> Set<E> enums(List<String> raw, Class<E> type, String name) {
        Set<E> out = EnumSet.noneOf(type);
        if (raw == null) {
            return out;
        }
        for (String value : raw) {
            if (value.isBlank()) {
                continue;
            }
            try {
                out.add(Enum.valueOf(type, value.trim().toUpperCase()));
            } catch (IllegalArgumentException e) {
                throw new InvalidQueryException("CHAT_INVALID_FILTER", "Valor no válido para " + name + ": " + value);
            }
        }
        return out;
    }

    private static Set<String> trimmedUpper(List<String> raw) {
        Set<String> out = new HashSet<>();
        raw.stream().map(String::trim).filter(s -> !s.isEmpty()).map(String::toUpperCase).forEach(out::add);
        return out;
    }

    /** Cursor opaco: base64url de {@code <instante ISO>|<id>}. */
    static String encode(ConversationCursor c) {
        return c == null ? null : Base64.getUrlEncoder().withoutPadding()
                .encodeToString((c.lastMessageAt() + "|" + c.id()).getBytes(StandardCharsets.UTF_8));
    }

    static ConversationCursor decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String[] parts = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8).split("\\|");
            return new ConversationCursor(Instant.parse(parts[0]), Long.parseLong(parts[1]));
        } catch (RuntimeException e) {
            throw new InvalidQueryException("CHAT_INVALID_CURSOR", "Cursor no válido");
        }
    }
}
