package com.parosurvivors.chat.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/** Cliente WebSocket de prueba: acumula los frames JSON recibidos y registra el código de cierre. */
public final class WsTestClient implements WebSocket.Listener, AutoCloseable {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final BlockingQueue<JsonNode> frames = new LinkedBlockingQueue<>();
    private final List<JsonNode> skipped = new ArrayList<>();
    private final CompletableFuture<Integer> closeCode = new CompletableFuture<>();
    private final StringBuilder partial = new StringBuilder();
    private WebSocket socket;

    public static WsTestClient connect(String url) {
        WsTestClient client = new WsTestClient();
        client.socket = HttpClient.newHttpClient().newWebSocketBuilder()
                .buildAsync(URI.create(url), client).join();
        return client;
    }

    @Override
    public void onOpen(WebSocket webSocket) {
        webSocket.request(1);
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        partial.append(data);
        if (last) {
            try {
                frames.add(MAPPER.readTree(partial.toString()));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            partial.setLength(0);
        }
        webSocket.request(1);
        return null;
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        closeCode.complete(statusCode);
        return null;
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        closeCode.complete(-1);
    }

    public void send(String type, String id, Map<String, Object> payload) {
        try {
            Map<String, Object> frame = new java.util.LinkedHashMap<>();
            frame.put("type", type);
            if (id != null) {
                frame.put("id", id);
            }
            frame.put("payload", payload);
            socket.sendText(MAPPER.writeValueAsString(frame), true).join();
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    public void sendRaw(String text) {
        socket.sendText(text, true).join();
    }

    /** Espera el siguiente frame del tipo dado (los de otros tipos quedan en {@link #skipped()}). */
    public JsonNode await(String type, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        try {
            while (true) {
                long left = deadline - System.nanoTime();
                JsonNode frame = left <= 0 ? null : frames.poll(left, TimeUnit.NANOSECONDS);
                if (frame == null) {
                    throw new AssertionError("No llegó un frame «" + type + "» en " + timeout
                            + ". Omitidos: " + skipped + " Pendientes: " + frames);
                }
                if (type.equals(frame.path("type").asText())) {
                    return frame;
                }
                skipped.add(frame);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    /** True si en el plazo NO llega ningún frame de ese tipo. */
    public boolean noFrame(String type, Duration within) {
        try {
            long deadline = System.nanoTime() + within.toNanos();
            while (true) {
                long left = deadline - System.nanoTime();
                JsonNode frame = left <= 0 ? null : frames.poll(left, TimeUnit.NANOSECONDS);
                if (frame == null) {
                    return true;
                }
                if (type.equals(frame.path("type").asText())) {
                    return false;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public List<JsonNode> skipped() {
        return skipped;
    }

    public int awaitClose(Duration timeout) {
        try {
            return closeCode.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            throw new AssertionError("La conexión no se cerró en " + timeout, e);
        }
    }

    public boolean isClosed() {
        return closeCode.isDone();
    }

    @Override
    public void close() {
        if (!closeCode.isDone()) {
            socket.sendClose(WebSocket.NORMAL_CLOSURE, "bye");
        }
    }
}
