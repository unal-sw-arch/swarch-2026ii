package com.parosurvivors.serviya.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.parosurvivors.serviya.shared.exception.handlers.RestAccessErrorHandler;
import com.parosurvivors.serviya.users.application.ports.output.UserReadPort;
import com.parosurvivors.serviya.users.infrastructure.security.JwtAuthenticationFilter;
import com.parosurvivors.serviya.users.infrastructure.security.JwtService;
import com.parosurvivors.serviya.shared.ratelimit.GlobalRateLimitFilter;
import com.parosurvivors.serviya.shared.ratelimit.RateLimiter;
import com.parosurvivors.serviya.users.infrastructure.security.RateLimitingFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.List;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtService jwtService;
    private final UserReadPort userReadPort;
    private final RestAccessErrorHandler restAccessErrorHandler;
    private final RateLimiter rateLimiter;
    private final ObjectMapper objectMapper;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource())) // permite al frontend (Vite :5173) llamar a la API
            .csrf(csrf -> csrf.disable()) // REST stateless API: no se usan cookies de sesion
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Autenticacion publica (login/registro/recuperacion) — RF-001/RF-002
                .requestMatchers("/api/v1/auth/**").permitAll()
                // Documentacion OpenAPI/Swagger
                .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                // El area propia del oferente (/offerers/me/**: perfil propio, disponibilidad) SIEMPRE exige
                // login: la identidad sale del JWT. Va ANTES de la regla publica de abajo, que si no
                // dejaria "me" abierto al hacer match con el comodin del id.
                .requestMatchers("/api/v1/offerers/me/**").authenticated()
                // Perfil publico del oferente — RF-027: accesible tambien por visitantes (sin JWT).
                .requestMatchers(HttpMethod.GET, "/api/v1/offerers/*", "/api/v1/offerers/*/**").permitAll()
                // Perfil y cuenta del usuario autenticado — RF-005/006/008/010/011.
                // La identidad SIEMPRE sale del JWT (CurrentUser.id()), nunca del body/path: exige login.
                .requestMatchers("/api/v1/users/me/**").authenticated()
                // Modulo admin (modulo 9): gestion de usuarios/roles, detalle admin y feedback -> solo ADMIN.
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                // Acciones de moderacion sobre un reporte (warn/ban/revert-feedback/close/mark-not-provided)
                // y el listado de acciones de un reporte -> solo ADMIN.
                .requestMatchers("/api/v1/reports/*/actions/**").hasRole("ADMIN")
                // Lecturas del panel de moderacion: listar todos los reportes y ver el detalle -> solo ADMIN.
                .requestMatchers(HttpMethod.GET, "/api/v1/reports").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/reports/*").hasRole("ADMIN")
                // Crear un reporte (requests/service-feedback/client-feedback): cualquier usuario autenticado
                // (el reporterId sale del JWT, nunca del body).
                .requestMatchers(HttpMethod.POST, "/api/v1/reports/**").authenticated()
                // Reportes recibidos/enviados de un usuario: autenticado; el chequeo propio-o-admin va en el
                // controlador (grano fino, patron role-security-split).
                .requestMatchers(HttpMethod.GET, "/api/v1/users/*/reports/**").authenticated()
                // Escrituras del catalogo de servicios (modulo 3): exigen login. La identidad sale del JWT
                // (CurrentUser.id()); el chequeo de dueño (grano fino) va en MarketplaceService.
                // (Las lecturas y el perfil publico del oferente ya estan cubiertos por reglas anteriores.)
                .requestMatchers(HttpMethod.POST, "/api/v1/services").authenticated()
                .requestMatchers(HttpMethod.PATCH, "/api/v1/services/*/soft-delete").authenticated()
                .requestMatchers(HttpMethod.PATCH, "/api/v1/services/*").authenticated()
                .requestMatchers(HttpMethod.DELETE, "/api/v1/services/*").authenticated()
                .requestMatchers(HttpMethod.POST,
                        "/api/v1/services/*/activate", "/api/v1/services/*/deactivate").authenticated()
                // Direcciones por id (modulo 2): las escrituras exigen login. La identidad sale del JWT
                // (CurrentUser.id()); el chequeo de dueño (grano fino) va en AddressService.
                .requestMatchers(HttpMethod.PATCH, "/api/v1/addresses/*").authenticated()
                .requestMatchers(HttpMethod.DELETE, "/api/v1/addresses/*").authenticated()
                // Crear una categoria (modulo 3): solo ADMIN. Las lecturas (GET) siguen publicas.
                .requestMatchers(HttpMethod.POST, "/api/v1/categories").hasRole("ADMIN")
                // (/api/v1/notifications/** y /api/v1/notification-channels ya NO viven aqui: el API gateway
                // las enruta a notifications-service, que aplica su propia autenticacion JWT.)
                // El resto se mantiene abierto por ahora (modulos aun no implementados)
                .anyRequest().permitAll()
            )
            // Sin token valido -> 401; token valido sin el rol -> 403. Cuerpo ErrorResponse uniforme
            // (mismo formato que GlobalExceptionHandler) via RestAccessErrorHandler. API stateless, sin login page.
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(restAccessErrorHandler)
                .accessDeniedHandler(restAccessErrorHandler))
            .addFilterBefore(new JwtAuthenticationFilter(jwtService, userReadPort), UsernamePasswordAuthenticationFilter.class)
            // Rate limiting por IP de /api/v1/auth/**, ANTES del filtro JWT: corta el abuso en la puerta
            // sin gastar ciclos validando tokens ni tocar la BD en peticiones que se van a rechazar igual.
            .addFilterBefore(new RateLimitingFilter(rateLimiter, objectMapper),
                    JwtAuthenticationFilter.class)
            // Techo global (api-default) DESPUES del JWT: asi el sujeto puede ser el usuario autenticado.
            .addFilterAfter(new GlobalRateLimitFilter(rateLimiter, objectMapper),
                    JwtAuthenticationFilter.class);

        return http.build();
    }

    /** CORS para el frontend en desarrollo (Vite). Permite login/registro/perfil desde el navegador. */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(
                "http://localhost:5173",
                "http://127.0.0.1:5173",
                "http://localhost:8081",
                "http://127.0.0.1:8081",
                "http://192.168.1.2:8081",
                "https://nonfervently-expiational-amani.ngrok-free.dev"
            ));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

}
