package com.parosurvivors.serviya.config;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.DefaultJOSEObjectTypeVerifier;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import com.parosurvivors.serviya.shared.exception.handlers.RestAccessErrorHandler;
import com.parosurvivors.serviya.users.infrastructure.security.JwtAudienceValidator;
import com.parosurvivors.serviya.users.infrastructure.security.JwtKeys;
import com.parosurvivors.serviya.users.infrastructure.security.JwtProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Seguridad del monolito como <b>resource server estándar</b> (Fase 4): valida el access token ES256 con la
 * clave pública del emisor (ES256 + {@code iss}/{@code aud}/{@code typ}/timestamps), igual que el gateway.
 * Sustituye la verificación propia ({@code JwtAuthenticationFilter}/{@code JwtService.resolve}).
 *
 * <p><b>Lo que ya NO vive aquí</b> (decisión #8, lo aplica el gateway): la autorización por rol por ruta
 * (los antiguos {@code hasRole("ADMIN")} pasan a {@code authenticated()}), la revalidación de roles/estado
 * por petición (M34: ahora la revocación es inmediata vía {@code min_iat} en el gateway), el CORS
 * (centralizado en el borde, T4.5) y TODO el rate limiting del borde: el techo global {@code api-default}
 * (T3.9) y las cuotas de auth por IP de {@code /api/v1/auth/**} (login/registro/reset) — ambos viven ahora
 * en el gateway (T4.9/P3.7; el {@code RateLimitingFilter} propio se eliminó). Control compensatorio: los
 * servicios solo son alcanzables a través del gateway (P3.8).</p>
 *
 * <p>En el monolito queda solo el rate limiting <b>domain-aware</b>: la cuota por CORREO de recuperación
 * ({@code reset-email}) en {@code AuthController} y el interceptor {@code @RateLimit} por endpoint.</p>
 */
@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtKeys jwtKeys;
    private final JwtProperties jwtProperties;
    private final RestAccessErrorHandler restAccessErrorHandler;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Decoder ES256 construido con la clave pública local del emisor (sin auto-llamada HTTP al JWKS): el
     * monolito ES el emisor y ya tiene la clave en {@link JwtKeys}. Acepta {@code typ=at+jwt} y valida
     * {@code iss}/{@code aud}/timestamps. Configuración equivalente a la del gateway (que sí lee el JWKS).
     */
    @Bean
    public JwtDecoder jwtDecoder() {
        ECKey jwk = new ECKey.Builder(Curve.P_256, jwtKeys.publicKey()).keyID(jwtKeys.keyId()).build();
        JWKSource<SecurityContext> jwkSource = new ImmutableJWKSet<>(new JWKSet(jwk));
        DefaultJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
        processor.setJWSTypeVerifier(new DefaultJOSEObjectTypeVerifier<>(
                new JOSEObjectType(jwtProperties.getTyp()), JOSEObjectType.JWT, null));
        processor.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.ES256, jwkSource));

        NimbusJwtDecoder decoder = new NimbusJwtDecoder(processor);
        OAuth2TokenValidator<Jwt> validators = new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(),
                new JwtIssuerValidator(jwtProperties.getIssuer()),
                new JwtAudienceValidator(jwtProperties.getAudience()));
        decoder.setJwtValidator(validators);
        return decoder;
    }

    /** Mapea el claim {@code roles} a authorities {@code ROLE_*} (para {@code CurrentUser.isAdmin()} y futuros gates). */
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
            // Sin CORS: lo resuelve el gateway (T4.5). REST stateless: sin cookies de sesión.
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Autenticacion publica (login/registro/recuperacion) — RF-001/RF-002
                .requestMatchers("/api/v1/auth/**").permitAll()
                // Documentacion OpenAPI/Swagger
                .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                // JWKS: clave publica para verificar los access tokens ES256. Publico por definicion (lo
                // consumen gateway y servicios por la red interna).
                .requestMatchers(HttpMethod.GET, "/.well-known/jwks.json").permitAll()
                // Area propia del oferente: SIEMPRE exige login (la identidad sale del JWT). Antes de la regla
                // publica de abajo para que "me" no haga match con el comodin del id.
                .requestMatchers("/api/v1/offerers/me/**").authenticated()
                // Perfil publico del oferente — RF-027: accesible por visitantes (sin JWT).
                .requestMatchers(HttpMethod.GET, "/api/v1/offerers/*", "/api/v1/offerers/*/**").permitAll()
                // Perfil y cuenta del usuario autenticado — RF-005/006/008/010/011.
                .requestMatchers("/api/v1/users/me/**").authenticated()
                // Modulo admin, acciones/lecturas de moderacion y crear categoria: el ROL lo aplica ahora el
                // GATEWAY (decision #8). Aqui solo se exige sesion valida; la logica de dominio va en los servicios.
                .requestMatchers("/api/v1/admin/**").authenticated()
                .requestMatchers("/api/v1/reports/*/actions/**").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/v1/reports", "/api/v1/reports/*").authenticated()
                .requestMatchers(HttpMethod.POST, "/api/v1/categories").authenticated()
                // Crear un reporte: cualquier usuario autenticado (el reporterId sale del JWT).
                .requestMatchers(HttpMethod.POST, "/api/v1/reports/**").authenticated()
                // Reportes recibidos/enviados de un usuario: autenticado; el chequeo propio-o-admin va en el controlador.
                .requestMatchers(HttpMethod.GET, "/api/v1/users/*/reports/**").authenticated()
                // Escrituras del catalogo de servicios (modulo 3): exigen login; el chequeo de dueño va en el servicio.
                .requestMatchers(HttpMethod.POST, "/api/v1/services").authenticated()
                .requestMatchers(HttpMethod.PATCH, "/api/v1/services/*/soft-delete").authenticated()
                .requestMatchers(HttpMethod.PATCH, "/api/v1/services/*").authenticated()
                .requestMatchers(HttpMethod.DELETE, "/api/v1/services/*").authenticated()
                .requestMatchers(HttpMethod.POST,
                        "/api/v1/services/*/activate", "/api/v1/services/*/deactivate").authenticated()
                // Direcciones por id (modulo 2): las escrituras exigen login; el chequeo de dueño va en AddressService.
                .requestMatchers(HttpMethod.PATCH, "/api/v1/addresses/*").authenticated()
                .requestMatchers(HttpMethod.DELETE, "/api/v1/addresses/*").authenticated()
                // El resto se mantiene abierto por ahora (modulos aun no implementados)
                .anyRequest().permitAll()
            )
            // Sin token valido -> 401; token valido sin permiso -> 403. Cuerpo uniforme via RestAccessErrorHandler.
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(restAccessErrorHandler)
                .accessDeniedHandler(restAccessErrorHandler))
            // Resource server: valida el JWT con el decoder ES256 y mapea roles->authorities.
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt
                .decoder(jwtDecoder())
                .jwtAuthenticationConverter(jwtAuthenticationConverter())));

        return http.build();
    }
}
