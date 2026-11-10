package com.parosurvivors.chat.realtime.infrastructure;

/** Canales del Pub/Sub propio del chat: uno por usuario ({@code chat:user:{id}}). */
final class RealtimeChannels {

    private RealtimeChannels() {
    }

    static String forUser(long userId) {
        return "chat:user:" + userId;
    }
}
