package com.parosurvivors.notifications.infrastructure.security;

import com.parosurvivors.notifications.infrastructure.web.RestAccessErrorHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Seguridad del servicio. A diferencia del monolito (que termina en {@code anyRequest().permitAll()},
 * ESTADO §3.1), aquí el default es DENEGAR: solo se abre lo declarado. CORS no se configura aquí: el
 * servicio no se expone al navegador directamente, lo hace el API gateway.
 */
@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtTokenVerifier jwtTokenVerifier;
    private final RestAccessErrorHandler restAccessErrorHandler;

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
            .addFilterBefore(new JwtAuthenticationFilter(jwtTokenVerifier), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
