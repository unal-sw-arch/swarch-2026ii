package com.parosurvivors.chat.shared.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.core.jackson.ModelResolver;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Contrato REST publicado (RNF-CHAT-21). El protocolo WebSocket y los eventos están en docs/asyncapi.yaml. */
@Configuration
class OpenApiConfig {

    /**
     * Sin esto springdoc usa su propio ObjectMapper y documenta los campos en camelCase, mientras la API responde
     * en snake_case (spring.jackson.property-naming-strategy): el contrato publicado mentiría.
     */
    @Bean
    ModelResolver modelResolver(ObjectMapper objectMapper) {
        return new ModelResolver(objectMapper);
    }

    @Bean
    OpenAPI chatOpenApi() {
        return new OpenAPI()
                .info(new Info().title("ServiYa chat-service").version("v1")
                        .description("Chat 1 a 1 cliente–oferente. Errores en formato RFC 9457 con un campo `code` estable. "
                                + "El tiempo real va por WebSocket en /api/chat/v1/ws (ver docs/asyncapi.yaml)."))
                .components(new Components().addSecuritySchemes("bearer", new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT (ES256)")))
                .addSecurityItem(new SecurityRequirement().addList("bearer"));
    }
}
