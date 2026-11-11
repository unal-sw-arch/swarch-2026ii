package com.parosurvivors.serviya.statistics.infrastructure.adapters.input;

import com.parosurvivors.serviya.shared.ratelimit.RateLimit;
import com.parosurvivors.serviya.statistics.application.dto.response.PlatformStatsResponse;
import com.parosurvivors.serviya.statistics.application.services.PlatformStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Estadisticas publicas de la plataforma. El endpoint y su contrato REST no cambian; solo se movio
 * la composicion fuera del kernel shared.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class StatisticsController {

    private final PlatformStatsService statsService;

    @GetMapping("/platform/stats")
    @RateLimit("stats")
    public ResponseEntity<PlatformStatsResponse> getPlatformStats() {
        return ResponseEntity.ok(statsService.getPlatformStats());
    }
}
