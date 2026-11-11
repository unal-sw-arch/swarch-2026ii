package com.parosurvivors.chat.shared.application;

/** Contrato de eventos del chat (guía §9). Los datos no llevan el contenido de los mensajes (DP-04). */
public final class ChatEvents {

    public static final String EXCHANGE = "serviya.chat";
    public static final String CONVERSATION_CREATED = "chat.conversation.created";
    public static final String CONVERSATION_READ_ONLY = "chat.conversation.read_only";
    public static final String MESSAGE_SENT = "chat.message.sent";

    private ChatEvents() {
    }
}
