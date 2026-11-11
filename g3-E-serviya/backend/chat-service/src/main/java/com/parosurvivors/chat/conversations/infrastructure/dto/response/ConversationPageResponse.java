package com.parosurvivors.chat.conversations.infrastructure.dto.response;

import java.util.List;

public record ConversationPageResponse(List<ConversationSummaryResponse> items, String nextCursor) {
}
