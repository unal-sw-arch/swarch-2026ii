package com.parosurvivors.serviya.shared.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.parosurvivors.serviya.shared.ratelimit.RateLimitInterceptor;
import com.parosurvivors.serviya.shared.ratelimit.RateLimiter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
@RequiredArgsConstructor
public class StaticResourceConfig implements WebMvcConfigurer {

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    /**
     * Nivel 2 del rate limiting. Se construye el interceptor aqui a partir del {@link RateLimiter}, que es
     * opcional (ObjectProvider) a proposito: en un slice @WebMvcTest este WebMvcConfigurer se carga pero no
     * hay RateLimiter, y no debe impedir que el contexto del slice arranque (simplemente no se registra).
     */
    private final ObjectProvider<RateLimiter> rateLimiterProvider;
    private final ObjectMapper objectMapper;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(uploadPath.toUri().toString());
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        RateLimiter rateLimiter = rateLimiterProvider.getIfAvailable();
        if (rateLimiter != null) {
            registry.addInterceptor(new RateLimitInterceptor(rateLimiter, objectMapper))
                    .addPathPatterns("/api/v1/**");
        }
    }
}
