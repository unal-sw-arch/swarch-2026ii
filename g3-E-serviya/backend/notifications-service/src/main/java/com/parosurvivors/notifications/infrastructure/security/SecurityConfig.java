package com.parosurvivors.notifications.infrastructure.security;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.proc.DefaultJOSEObjectTypeVerifier;
import com.parosurvivors.notifications.infrastructure.web.RestAccessErrorHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Seguridad del servicio como <b>resource server estándar</b> (Fase 4): verifica el access token ES256 del
 * emisor leyendo su JWKS (igual que el gateway; notif NO es el emisor, no tiene la clave). Sustituye la
 * verificación HS256 propia ({@code JwtTokenVerifier}/{@code JwtAuthenticationFilter}).
 *
 * <p>A diferencia del monolito, el default es DENEGAR: solo se abre lo declarado. CORS no se configura aquí:
 * el servicio no se expone al navegador directamente, lo hace el API gateway. No revalida el estado de la
 * cuenta (esa tabla no es suya): la revocación inmediata la aplica el gateway vía {@code min_iat}.</p>
 */
@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final RestAccessErrorHandler restAccessErrorHandler;

    @Value("${serviya.security.jwt.jwks-uri}") private String jwksUri;
    @Value("${serviya.security.jwt.issuer}") private String issuer;
    @Value("${serviya.security.jwt.audience}") private String audience;
    @Value("${serviya.security.jwt.typ:at+jwt}") private String typ;

    /** Decoder ES256 que lee el JWKS del emisor (carga perezosa ⇒ arranca aunque el JWKS no esté todavía). */
    @Bean
    public JwtDecoder jwtDecoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwksUri)
                .jwsAlgorithm(SignatureAlgorithm.ES256)
                .jwtProcessorCustomizer(processor -> processor.setJWSTypeVerifier(
                        new DefaultJOSEObjectTypeVerifier<>(new JOSEObjectType(typ), JOSEObjectType.JWT, null)))
                .build();
        OAuth2TokenValidator<Jwt> validators = new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(),
                new JwtIssuerValidator(issuer),
                new JwtAudienceValidator(audience));
        decoder.setJwtValidator(validators);
        return decoder;
    }

    /** Mapea el claim {@code roles} a authorities {@code ROLE_*} (consistente con el resto de servicios). */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable()) // API REST stateless: sin cookies de sesión
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Bandeja del usuario autenticado: la identidad sale del JWT (CurrentUser.id()).
                .requestMatchers("/api/v1/notifications", "/api/v1/notifications/**").authenticated()
                .requestMatchers("/api/v1/notification-channels").authenticated()
                // Liveness/readiness para Docker y el balanceador.
                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                // Documentación OpenAPI del servicio.
                .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                .anyRequest().denyAll()
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(restAccessErrorHandler)
                .accessDeniedHandler(restAccessErrorHandler))
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt
                .decoder(jwtDecoder())
                .jwtAuthenticationConverter(jwtAuthenticationConverter())));

        return http.build();
    }
}
