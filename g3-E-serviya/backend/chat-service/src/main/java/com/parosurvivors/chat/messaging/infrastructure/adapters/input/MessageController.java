package com.parosurvivors.chat.messaging.infrastructure.adapters.input;

import com.parosurvivors.chat.messaging.application.services.GetConversationHistoryService;
import com.parosurvivors.chat.messaging.application.services.SendMessageService;
import com.parosurvivors.chat.messaging.infrastructure.dto.form.SendMessageForm;
import com.parosurvivors.chat.messaging.infrastructure.dto.response.MessagePageResponse;
import com.parosurvivors.chat.messaging.infrastructure.dto.response.MessageResponse;
import com.parosurvivors.chat.messaging.infrastructure.dto.response.SendMessageResponse;
import com.parosurvivors.chat.shared.infrastructure.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
@RequestMapping("/api/chat/v1/conversations/{conversationId}/messages")
class MessageController {

    private final GetConversationHistoryService history;
    private final SendMessageService send;

    MessageController(GetConversationHistoryService history, SendMessageService send) {
        this.history = history;
        this.send = send;
    }

    /** Historial paginado por secuencia (RF-CHAT-12): {@code before_seq} hacia atrás o {@code after_seq} hacia adelante. */
    @GetMapping
    MessagePageResponse history(@AuthenticationPrincipal Jwt jwt, @PathVariable long conversationId,
                                @RequestParam(name = "before_seq", required = false) Long beforeSeq,
                                @RequestParam(name = "after_seq", required = false) Long afterSeq,
                                @RequestParam(required = false) Integer limit) {
        return new MessagePageResponse(history.execute(CurrentUser.id(jwt), conversationId, beforeSeq, afterSeq, limit)
                .stream().map(MessageResponse::of).toList());
    }

    /** Envío por REST: respaldo si el WebSocket no está disponible. 201 si es nuevo, 200 si es un reenvío reconocido. */
    @PostMapping
    ResponseEntity<SendMessageResponse> send(@AuthenticationPrincipal Jwt jwt, @PathVariable long conversationId,
                                             @RequestBody SendMessageForm form) {
        var result = send.execute(new SendMessageService.Command(CurrentUser.id(jwt), conversationId,
                form.clientMessageId(), form.body()));
        return ResponseEntity.status(result.duplicate() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(SendMessageResponse.of(result));
    }
}
