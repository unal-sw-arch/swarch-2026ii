package com.parosurvivors.notifications.infrastructure.adapters.output;

import static org.assertj.core.api.Assertions.assertThat;

import com.parosurvivors.notifications.infrastructure.config.EmailProperties;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/**
 * Contrato clave de {@link EmailApiAdapter}: {@code send} NUNCA lanza. Ante fallos devuelve false.
 */
class EmailApiAdapterTest {

    private EmailApiAdapter adapter() {
        // Puerto 1 = conexión rechazada al instante: simula el proveedor caído.
        EmailProperties props = new EmailProperties(
                true, "http://localhost:1", "test-key", "no-reply@serviya.com", "ServiYa", 500, 500);
        return new EmailApiAdapter(RestClient.builder().baseUrl(props.providerUrl()).build(), props);
    }

    @Test
    void send_returnsFalse_whenRecipientEmailIsMissing() {
        assertThat(adapter().send(null, "welcome", "Título", "Mensaje", Map.of())).isFalse();
        assertThat(adapter().send("  ", "welcome", "Título", "Mensaje", Map.of())).isFalse();
    }

    @Test
    void send_returnsFalse_andDoesNotThrow_whenProviderIsUnreachable() {
        boolean result = adapter().send("ana@serviya.com", "welcome", "Título", "Mensaje", Map.of());

        assertThat(result).isFalse();
    }
}
