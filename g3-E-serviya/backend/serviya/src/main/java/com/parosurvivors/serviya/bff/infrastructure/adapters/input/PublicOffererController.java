package com.parosurvivors.serviya.bff.infrastructure.adapters.input;

import com.parosurvivors.serviya.bff.application.services.PublicOffererQueryService;
import com.parosurvivors.serviya.bff.infrastructure.adapters.input.api.PublicOffererApi;
import com.parosurvivors.serviya.bff.infrastructure.dto.response.OffererProfileSummaryResponse;
import com.parosurvivors.serviya.bff.infrastructure.dto.response.OffererPublicProfileDetailResponse;
import com.parosurvivors.serviya.bff.infrastructure.mappers.BffWebMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Composicion HTTP de las lecturas publicas del oferente. Mantiene las mismas URLs y JSON que los
 * endpoints historicos de profiles.
 */
@RestController
@RequiredArgsConstructor
public class PublicOffererController implements PublicOffererApi {

    private final PublicOffererQueryService publicOffererQueryService;
    private final BffWebMapper mapper;

    @Override
    @GetMapping("/api/v1/offerers/{id}/public-profile")
    public ResponseEntity<OffererPublicProfileDetailResponse> getPublicProfileDetail(@PathVariable Long id) {
        return ResponseEntity.ok(mapper.toResponse(publicOffererQueryService.getPublicProfile(id)));
    }

    @Override
    @GetMapping("/api/v1/offerers/{id}/summary")
    public ResponseEntity<OffererProfileSummaryResponse> getProfileSummary(@PathVariable Long id) {
        return ResponseEntity.ok(mapper.toResponse(publicOffererQueryService.getSummary(id)));
    }
}
