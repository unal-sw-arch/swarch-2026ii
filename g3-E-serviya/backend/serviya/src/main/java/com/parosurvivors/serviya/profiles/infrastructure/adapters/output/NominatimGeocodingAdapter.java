package com.parosurvivors.serviya.profiles.infrastructure.adapters.output;

import com.parosurvivors.serviya.profiles.application.dto.result.CoordinatesResult;
import com.parosurvivors.serviya.profiles.application.ports.output.GeocodingPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

/**
 * Implementación de GeocodingPort usando la API Nominatim de OpenStreetMap.
 * <p>
 * Nominatim es un servicio de geocodificación gratuito basado en datos de OpenStreetMap.
 * No requiere API key pero tiene límites de uso (1 req/seg según política de uso).
 * </p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NominatimGeocodingAdapter implements GeocodingPort {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${app.geocoding.nominatim.base-url:https://nominatim.openstreetmap.org}")
    private String baseUrl;

    @Value("${app.geocoding.nominatim.user-agent:ServiYa/1.0}")
    private String userAgent;

    @Value("${app.geocoding.nominatim.timeout-seconds:5}")
    private int timeoutSeconds;

    @Value("${app.geocoding.enabled:true}")
    private boolean enabled;

    @Override
    public Optional<CoordinatesResult> geocode(String addressLine, String city) {
        if (!enabled) {
            log.debug("Geocoding está deshabilitado, devolviendo vacío para: {} {}", addressLine, city);
            return Optional.empty();
        }

        String query = addressLine + ", " + city;
        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
        String url = baseUrl + "/search?q=" + encodedQuery + "&format=json&limit=1&addressdetails=1";

        log.debug("Geocodificando dirección: {} (URL: {})", query, url);

        try {
            JsonNode response = webClient.get()
                    .uri(url)
                    .header(HttpHeaders.USER_AGENT, userAgent)
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .onErrorResume(WebClientResponseException.class, ex -> {
                        log.warn("Error HTTP al geocodificar '{}': {} - {}", query, ex.getStatusCode(), ex.getResponseBodyAsString());
                        return Mono.empty();
                    })
                    .onErrorResume(Exception.class, ex -> {
                        log.warn("Error al geocodificar '{}': {}", query, ex.getMessage());
                        return Mono.empty();
                    })
                    .block();

            if (response != null && response.isArray() && response.size() > 0) {
                JsonNode firstResult = response.get(0);
                BigDecimal latitude = new BigDecimal(firstResult.get("lat").asText());
                BigDecimal longitude = new BigDecimal(firstResult.get("lon").asText());

                log.debug("Geocodificación exitosa para '{}': lat={}, lon={}", query, latitude, longitude);
                return Optional.of(new CoordinatesResult(latitude, longitude));
            }

            log.debug("No se encontraron resultados para: {}", query);
            return Optional.empty();

        } catch (Exception e) {
            log.error("Error inesperado al geocodificar '{}': {}", query, e.getMessage(), e);
            return Optional.empty();
        }
    }
}