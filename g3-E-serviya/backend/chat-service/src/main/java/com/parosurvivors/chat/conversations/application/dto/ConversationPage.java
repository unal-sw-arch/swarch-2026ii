package com.parosurvivors.chat.conversations.application.dto;

import java.util.List;

public record ConversationPage(List<ConversationSummary> items, ConversationCursor nextCursor) {
}
