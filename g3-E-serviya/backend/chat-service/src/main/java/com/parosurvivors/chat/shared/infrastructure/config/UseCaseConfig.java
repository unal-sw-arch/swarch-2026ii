package com.parosurvivors.chat.shared.infrastructure.config;

import com.parosurvivors.chat.conversations.application.ports.output.ConversationReadModel;
import com.parosurvivors.chat.conversations.application.ports.output.ConversationRepository;
import com.parosurvivors.chat.conversations.application.services.ApplyRequestStatusService;
import com.parosurvivors.chat.conversations.application.services.GetConversationService;
import com.parosurvivors.chat.conversations.application.services.ListMyConversationsService;
import com.parosurvivors.chat.conversations.application.services.MarkAsReadService;
import com.parosurvivors.chat.conversations.application.services.OpenRequestConversationService;
import com.parosurvivors.chat.conversations.domain.TerminalRequestStatuses;
import com.parosurvivors.chat.directory.application.ports.output.UserDirectory;
import com.parosurvivors.chat.directory.application.services.UserProjectionService;
import com.parosurvivors.chat.messaging.application.ports.output.MessageRepository;
import com.parosurvivors.chat.messaging.application.services.GetConversationHistoryService;
import com.parosurvivors.chat.messaging.application.services.SendMessageService;
import com.parosurvivors.chat.messaging.application.services.SyncMessagesService;
import com.parosurvivors.chat.realtime.application.ports.WsTicketStore;
import com.parosurvivors.chat.realtime.application.services.WsTicketService;
import com.parosurvivors.chat.shared.application.ports.EventPublisher;
import com.parosurvivors.chat.shared.application.ports.NotificationRequester;
import com.parosurvivors.chat.shared.application.ports.OnlineUsers;
import com.parosurvivors.chat.shared.application.ports.RateLimiter;
import com.parosurvivors.chat.shared.application.ports.RealtimeNotifier;
import com.parosurvivors.chat.shared.application.ports.UnitOfWork;
import java.time.Clock;
import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Raíz de composición: los casos de uso son clases planas (sin anotaciones de Spring, para que la capa de
 * aplicación no dependa del framework) y se instancian aquí con sus puertos.
 */
@Configuration
class UseCaseConfig {

    @Bean
    TerminalRequestStatuses terminalRequestStatuses(
            @Value("${serviya.chat.terminal-request-statuses}") String statuses) {
        return new TerminalRequestStatuses(java.util.Set.copyOf(Arrays.asList(statuses.split(","))));
    }

    @Bean
    OpenRequestConversationService openRequestConversationService(ConversationRepository conversations,
            EventPublisher events, RealtimeNotifier realtime, UnitOfWork uow, TerminalRequestStatuses terminal) {
        return new OpenRequestConversationService(conversations, events, realtime, uow, terminal);
    }

    @Bean
    ApplyRequestStatusService applyRequestStatusService(ConversationRepository conversations, EventPublisher events,
            RealtimeNotifier realtime, UnitOfWork uow, TerminalRequestStatuses terminal) {
        return new ApplyRequestStatusService(conversations, events, realtime, uow, terminal);
    }

    @Bean
    ListMyConversationsService listMyConversationsService(ConversationReadModel readModel) {
        return new ListMyConversationsService(readModel);
    }

    @Bean
    GetConversationService getConversationService(ConversationRepository conversations) {
        return new GetConversationService(conversations);
    }

    @Bean
    MarkAsReadService markAsReadService(ConversationRepository conversations, RealtimeNotifier realtime) {
        return new MarkAsReadService(conversations, realtime);
    }

    @Bean
    SendMessageService sendMessageService(ConversationRepository conversations, MessageRepository messages,
            UserDirectory users, EventPublisher events, NotificationRequester notifications, RealtimeNotifier realtime,
            OnlineUsers online, RateLimiter rateLimiter, UnitOfWork uow, Clock clock,
            @Value("${serviya.chat.message.max-length}") int maxLength) {
        return new SendMessageService(conversations, messages, users, events, notifications, realtime, online,
                rateLimiter, uow, clock, maxLength);
    }

    @Bean
    GetConversationHistoryService getConversationHistoryService(ConversationRepository conversations,
            MessageRepository messages) {
        return new GetConversationHistoryService(conversations, messages);
    }

    @Bean
    SyncMessagesService syncMessagesService(ConversationRepository conversations, MessageRepository messages) {
        return new SyncMessagesService(conversations, messages);
    }

    @Bean
    UserProjectionService userProjectionService(UserDirectory directory) {
        return new UserProjectionService(directory);
    }

    @Bean
    WsTicketService wsTicketService(WsTicketStore store) {
        return new WsTicketService(store);
    }
}
