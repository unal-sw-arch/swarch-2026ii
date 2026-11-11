package com.parosurvivors.chat.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

/**
 * Contrato REST versionado (guía §15.5: «el PR no se aprueba si cambia un contrato sin actualizar OpenAPI»).
 * Compara el OpenAPI que genera la app con {@code docs/openapi.json}. Tras un cambio intencional se regenera con:
 * {@code mvn test -Dtest=OpenApiContractIntegrationTest -Dopenapi.write=true -DargLine="-Dapi.version=1.44 -Dopenapi.write=true"}.
 */
class OpenApiContractIntegrationTest extends IntegrationTestBase {

    private static final Path CONTRACT = Path.of("docs/openapi.json");

    @Autowired TestRestTemplate http;

    @Test
    void el_openapi_generado_coincide_con_el_contrato_versionado_y_usa_snake_case() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token(1));
        String raw = http.exchange("/v3/api-docs", HttpMethod.GET, new HttpEntity<>(headers), String.class).getBody();
        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
        JsonNode generated = mapper.readTree(raw);
        ((com.fasterxml.jackson.databind.node.ObjectNode) generated).remove("servers"); // depende del puerto aleatorio

        JsonNode participant = generated.path("components").path("schemas").path("Participant").path("properties");
        assertThat(participant.has("user_id")).as("los nombres deben ser snake_case, como responde la API").isTrue();
        assertThat(participant.has("userId")).isFalse();

        String pretty = mapper.writeValueAsString(mapper.treeToValue(generated, Object.class)) + "\n";
        if (Boolean.getBoolean("openapi.write") || !Files.exists(CONTRACT)) {
            Files.createDirectories(CONTRACT.getParent());
            Files.writeString(CONTRACT, pretty);
        }
        assertThat(mapper.readTree(Files.readString(CONTRACT)))
                .as("El API cambió: revisa la compatibilidad y regenera docs/openapi.json (ver javadoc)")
                .isEqualTo(mapper.readTree(pretty));
    }
}
