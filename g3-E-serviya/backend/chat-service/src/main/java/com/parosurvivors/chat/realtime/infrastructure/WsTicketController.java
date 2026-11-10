package com.parosurvivors.chat.realtime.infrastructure;

import com.parosurvivors.chat.realtime.application.services.WsTicketService;
import com.parosurvivors.chat.shared.infrastructure.security.CurrentUser;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat/v1")
class WsTicketController {

    private final WsTicketService tickets;

    WsTicketController(WsTicketService tickets) {
        this.tickets = tickets;
    }

    /** Ticket de un solo uso (~30 s) para abrir {@code /api/chat/v1/ws?ticket=...} sin poner el JWT en la URL. */
    @PostMapping("/ws-tickets")
    Map<String, Object> issue(@AuthenticationPrincipal Jwt jwt) {
        var issued = tickets.issue(CurrentUser.id(jwt));
        return Map.of("ticket", issued.ticket(), "expires_in", issued.expiresInSeconds());
    }
}
