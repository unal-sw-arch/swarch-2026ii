package com.parosurvivors.gateway.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.proc.DefaultJOSEObjectTypeVerifier;
import com.parosurvivors.gateway.error.GatewayAccessDeniedHandler;
import com.parosurvivors.gateway.error.GatewayAuthenticationEntryPoint;
import com.parosurvivors.gateway.error.GatewayErrorWriter;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import reactor.core.publisher.Flux;

/**
 * Seguridad del gateway (Fase 3): valida el access token ES256 contra el JWKS del emisor y aplica la
 * autorización gruesa por ruta. Las piezas:
 *
 * <ul>
 *   <li><b>Decoder ES256</b> ({@link #jwtDecoder}): fija el algoritmo (el default de Nimbus es RS256),
 *       acepta el {@code typ: at+jwt} del contrato (Nimbus solo acepta {@code JWT}/ausente por defecto) y
 *       valida {@code iss}, {@code aud} y timestamps con tolerancia de reloj. Carga las claves de forma
 *       perezosa, así el gateway arranca aunque el JWKS no esté disponible todavía (T3.12).</li>
 *   <li><b>roles → authorities</b> ({@link #jwtAuthenticationConverter}): el claim {@code roles} se mapea
 *       a {@code ROLE_*} para que {@code hasRole(...)} funcione.</li>
 *   <li><b>Matriz de rutas</b> ({@link #securityWebFilterChain}): públicas (P3.3), ADMIN (P3.4) y el resto
 *       autenticado. El {@code min_iat} (P3.1) y el formato de error 4.4 llegan en slices posteriores.</li>
 * </ul>
 */
@Configuration
@EnableWebFluxSecurity
public class GatewaySecurityConfig {

    private final String jwksUri;
    private final String issuer;
    private final String audience;
    private final Duration clockSkew;

    public GatewaySecurityConfig(
            @Value("${serviya.security.jwt.jwks-uri}") String jwksUri,
            @Value("${serviya.security.jwt.issuer}") String issuer,
            @Value("${serviya.security.jwt.audience}") String audience,
            @Value("${serviya.security.jwt.clock-skew:60s}") Duration clockSkew) {
        this.jwksUri = jwksUri;
        this.issuer = issuer;
        this.audience = audience;
        this.clockSkew = clockSkew;
    }

    /**
     * Circuit breaker de {@code redis-coord} para el chequeo de {@code min_iat}: abre rápido ante fallos
     * (5 llamadas, &gt;50% error) y se mantiene abierto 5 s antes de tantear media-apertura, de modo que un
     * {@code redis-coord} caído se corta al instante en el hot-path en vez de pagar el timeout por request.
     */
    @Bean
    public CircuitBreaker redisCoordCircuitBreaker() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .failureRateThreshold(50f)
                .waitDurationInOpenState(Duration.ofSeconds(5))
                .build();
        return CircuitBreaker.of("redis-coord", config);
    }

    /**
     * Decoder efectivo: valida el token ES256 (firma + {@code iss}/{@code aud}/timestamps) y luego aplica
     * {@code min_iat} contra {@code redis-coord} ({@link MinIatReactiveJwtDecoder}).
     */
    @Bean
    public ReactiveJwtDecoder jwtDecoder(ReactiveStringRedisTemplate coordRedis, MeterRegistry meters,
                                         CircuitBreaker redisCoordCircuitBreaker) {
        return new MinIatReactiveJwtDecoder(nimbusDecoder(), coordRedis, meters, redisCoordCircuitBreaker);
    }

    /** Decoder base: valida firma ES256, typ, iss, aud y timestamps (sin min_iat). */
    private ReactiveJwtDecoder nimbusDecoder() {
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withJwkSetUri(jwksUri)
                .jwsAlgorithm(SignatureAlgorithm.ES256)
                // Acepta el header typ=at+jwt (RFC 9068) además de JWT/ausente (robustez).
                .jwtProcessorCustomizer(processor -> processor.setJWSTypeVerifier(
                        new DefaultJOSEObjectTypeVerifier<>(
                                new JOSEObjectType("at+jwt"), JOSEObjectType.JWT, null)))
                .build();

        OAuth2TokenValidator<Jwt> validators = new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(clockSkew),
                new JwtIssuerValidator(issuer),
                new JwtAudienceValidator(audience));
        decoder.setJwtValidator(validators);
        return decoder;
    }

    /** Mapea el claim {@code roles} (lista) a authorities {@code ROLE_<NOMBRE>}. */
    @Bean
    public ReactiveJwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix("ROLE_");

        ReactiveJwtAuthenticationConverter converter = new ReactiveJwtAuthenticationConverter();
        // JwtGrantedAuthoritiesConverter es síncrono (Collection); se adapta a Flux para el converter reactivo.
        converter.setJwtGrantedAuthoritiesConverter(jwt -> Flux.fromIterable(authorities.convert(jwt)));
        return converter;
    }

    /** Cuerpo de error 4.4 compartido por el entry point (401) y el access denied handler (403). */
    @Bean
    public GatewayErrorWriter gatewayErrorWriter(ObjectMapper objectMapper, MeterRegistry meters) {
        return new GatewayErrorWriter(objectMapper, meters);
    }

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(
            ServerHttpSecurity http,
            ReactiveJwtDecoder jwtDecoder,
            ReactiveJwtAuthenticationConverter jwtAuthenticationConverter,
            GatewayErrorWriter errorWriter) {
        GatewayAuthenticationEntryPoint entryPoint = new GatewayAuthenticationEntryPoint(errorWriter);
        GatewayAccessDeniedHandler accessDeniedHandler = new GatewayAccessDeniedHandler(errorWriter);
        http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                // Formato de error 4.4 uniforme (T3.7) para fallos de auth/authz fuera del resource server.
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                // CORS lo resuelve globalcors de Spring Cloud Gateway (P3.6); aquí no se duplica.
                .authorizeExchange(exchange -> exchange
                        // Preflight CORS sin token.
                        .pathMatchers(HttpMethod.OPTIONS).permitAll()
                        // WebSocket del chat: lo autentica el chat-service con un ticket de un solo uso en la
                        // query (los navegadores no pueden poner Authorization en el handshake WS), así que el
                        // borde lo deja pasar. Va ANTES de /api/chat/** para no exigirle Bearer. El JWKS NO se
                        // expone aquí (P3.3): el chat lo lee directo del monolito por la red interna.
                        .pathMatchers("/api/chat/v1/ws").permitAll()
                        // El área propia del oferente exige login; va ANTES del perfil público de abajo.
                        .pathMatchers("/api/v1/offerers/me/**").authenticated()
                        // --- Rutas públicas (P3.3) ---
                        .pathMatchers(HttpMethod.POST,
                                "/api/v1/auth/login",
                                "/api/v1/auth/register",
                                "/api/v1/auth/refresh",
                                "/api/v1/auth/password-reset",
                                "/api/v1/auth/password-reset/validate",
                                "/api/v1/auth/password-reset/confirm").permitAll()
                        .pathMatchers(HttpMethod.GET,
                                "/api/v1/services",
                                "/api/v1/services/*",
                                "/api/v1/categories",
                                "/api/v1/offerers/*",
                                "/api/v1/offerers/*/**").permitAll()
                        // JWKS NO es pública en el borde (P3.3, corrección): el gateway no la expone a
                        // internet; sus consumidores (gateway/servicios) la leen por la red interna.
                        // --- Autorización gruesa por rol (P3.4): solo ADMIN ---
                        .pathMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .pathMatchers("/api/v1/reports/*/actions/**").hasRole("ADMIN")
                        .pathMatchers(HttpMethod.GET, "/api/v1/reports", "/api/v1/reports/*").hasRole("ADMIN")
                        .pathMatchers(HttpMethod.POST, "/api/v1/categories").hasRole("ADMIN")
                        // Todo lo demás exige autenticación.
                        .anyExchange().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                        .jwt(jwt -> jwt
                                .jwtDecoder(jwtDecoder)
                                .jwtAuthenticationConverter(jwtAuthenticationConverter)));
        return http.build();
    }
}
