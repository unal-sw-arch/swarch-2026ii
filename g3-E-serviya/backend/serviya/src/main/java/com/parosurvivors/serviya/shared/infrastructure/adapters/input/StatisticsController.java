package com.parosurvivors.serviya.shared.infrastructure.adapters.input;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.parosurvivors.serviya.shared.dto.response.PlatformStatsResponse;
import com.parosurvivors.serviya.shared.infrastructure.repositories.PlatformStatsRepository;
import com.parosurvivors.serviya.shared.ratelimit.RateLimit;

import lombok.RequiredArgsConstructor;

/**
 * Controlador de estadísticas públicas de la plataforma.
 * No requiere autenticación.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class StatisticsController {
    
    private final PlatformStatsRepository statsRepository;
    
    @GetMapping("/platform/stats")
    @RateLimit("stats")
    public ResponseEntity<PlatformStatsResponse> getPlatformStats() {
        PlatformStatsResponse stats = statsRepository.getPlatformStats();
        return ResponseEntity.ok(stats);
    }
}
