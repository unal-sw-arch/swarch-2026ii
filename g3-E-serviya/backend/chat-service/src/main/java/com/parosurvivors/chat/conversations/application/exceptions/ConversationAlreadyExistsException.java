package com.parosurvivors.chat.conversations.application.exceptions;

/** Lanzada por el repositorio cuando se viola la unicidad de la conversación de una solicitud (RF-CHAT-02). */
public class ConversationAlreadyExistsException extends RuntimeException {

    public ConversationAlreadyExistsException(long requestId) {
        super("Ya existe una conversación para la solicitud " + requestId);
    }
}
