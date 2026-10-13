package com.parosurvivors.notifications.infrastructure.adapters.output;

import com.parosurvivors.notifications.application.ports.output.EmailPort;
import com.parosurvivors.notifications.infrastructure.config.EmailProperties;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Adaptador de salida de {@link EmailPort} contra la API transaccional de Brevo. Se crea solo cuando
 * {@code serviya.email.enabled=true} (si no, no hay bean y la entrega EMAIL degrada a FAILED, sin romper).
 *
 * <p><b>Regla de oro</b>: {@code send} NUNCA lanza. Ante cualquier fallo (sin correo, timeout, 4xx/5xx del
 * proveedor) captura, loguea y devuelve {@code false}: la entrega queda FAILED y el reintento programado se
 * encargará. El correo del destinatario llega resuelto (viaja en el mensaje del bus): ya no se consulta a
 * {@code users}, que era la única dependencia del módulo hacia otro módulo del monolito.</p>
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "serviya.email", name = "enabled", havingValue = "true")
public class EmailApiAdapter implements EmailPort {

    private static final Logger log = LoggerFactory.getLogger(EmailApiAdapter.class);

    private final RestClient emailRestClient;
    private final EmailProperties props;

    @Override
    public boolean send(String recipientEmail, String type, String title, String message,
                        Map<String, String> protectedData) {
        try {
            if (recipientEmail == null || recipientEmail.isBlank()) {
                log.warn("Notification of type {} has no recipient email — skipping EMAIL send", type);
                return false;
            }

            BrevoEmailRequest payload = new BrevoEmailRequest(
                    new Sender(props.fromName(), props.fromEmail()),
                    List.of(new Recipient(recipientEmail)),
                    title,
                    buildHtml(title, message, protectedData));

            emailRestClient.post()
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity(); // 4xx/5xx lanzan RestClientResponseException → capturado abajo

            return true;
        } catch (Exception e) {
            // No propagar jamás: solo esta entrega falla (queda FAILED para el reintento). No se loguea el
            // correo del destinatario (PII), solo el tipo.
            log.warn("Email send for notification type {} failed: {}", type, e.getMessage());
            return false;
        }
    }

    /** Envuelve título + mensaje (texto plano, posible contenido de usuario) en un HTML mínimo y escapado. */
    private String buildHtml(String title, String message, Map<String, String> data) {
        String actionUrl = data == null ? null : data.get("actionUrl");
        // El texto del enlace es configurable por el emisor: "Ver detalle" no sirve para todo (el correo
        // de recuperación de contraseña necesita una llamada a la acción explícita).
        String actionLabel = data == null ? null : data.get("actionLabel");
        String cta = actionUrl == null ? "" : "<p><a href=\"" + escape(actionUrl)
                + "\" style=\"color:#2b6cb0\">"
                + escape(actionLabel == null ? "Ver detalle" : actionLabel) + "</a></p>";
        return """
                <div style="font-family:Arial,Helvetica,sans-serif;max-width:560px;margin:0 auto;padding:16px">
                  <h2 style="color:#2b6cb0;margin:0 0 12px">%s</h2>
                  <p style="font-size:15px;line-height:1.5;color:#222">%s</p>
                  %s
                  <hr style="border:none;border-top:1px solid #eee;margin:20px 0">
                  <small style="color:#888">ServiYa · notificación automática. No respondas a este correo.</small>
                </div>
                """.formatted(escape(title), escape(message), cta);
    }

    /** Escapa los caracteres HTML sensibles para evitar inyección en el cuerpo del correo. */
    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    // ===== Esquema del payload de Brevo (POST /v3/smtp/email) =====
    private record BrevoEmailRequest(Sender sender, List<Recipient> to, String subject, String htmlContent) {
    }

    private record Sender(String name, String email) {
    }

    private record Recipient(String email) {
    }
}
