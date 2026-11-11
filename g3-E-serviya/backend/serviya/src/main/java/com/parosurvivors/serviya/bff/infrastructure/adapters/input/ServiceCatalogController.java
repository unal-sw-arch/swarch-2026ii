package com.parosurvivors.serviya.bff.infrastructure.adapters.input;

import com.parosurvivors.serviya.bff.application.dto.ServiceSearchRequest;
import com.parosurvivors.serviya.bff.application.services.ServiceCatalogQueryService;
import com.parosurvivors.serviya.bff.infrastructure.adapters.input.api.ServiceCatalogApi;
import com.parosurvivors.serviya.bff.infrastructure.dto.response.ServiceDetailResponse;
import com.parosurvivors.serviya.bff.infrastructure.dto.response.ServiceResponse;
import com.parosurvivors.serviya.bff.infrastructure.mappers.BffWebMapper;
import com.parosurvivors.serviya.shared.ratelimit.RateLimit;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/**
 * Composicion HTTP de las lecturas del catalogo. Mantiene las URLs y el JSON historicos.
 */
@RestController
@RequiredArgsConstructor
public class ServiceCatalogController implements ServiceCatalogApi {

    private final ServiceCatalogQueryService serviceCatalogQueryService;
    private final BffWebMapper mapper;

    @Override
    @GetMapping("/api/v1/services/{id}/detail")
    public ResponseEntity<ServiceDetailResponse> getDetail(@PathVariable Long id) {
        return ResponseEntity.ok(mapper.toResponse(serviceCatalogQueryService.getDetail(id)));
    }

    @Override
    @GetMapping("/api/v1/offerers/{offererId}/services")
    public ResponseEntity<List<ServiceDetailResponse>> getByOffererId(@PathVariable Long offererId) {
        return ResponseEntity.ok(serviceCatalogQueryService.getByOfferer(offererId).stream()
                .map(mapper::toResponse)
                .toList());
    }

    @Override
    @GetMapping("/api/v1/services/search")
    @RateLimit("search")
    public ResponseEntity<Page<ServiceResponse>> search(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long offererId,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Boolean available,
            @RequestParam(required = false) Double minRating,
            @RequestParam(required = false) Double maxRating,
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude,
            @RequestParam(required = false) Double maxDistanceKm,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {

        ServiceSearchRequest request = new ServiceSearchRequest(
                name, categoryId, offererId, minPrice, maxPrice, available,
                minRating, maxRating, latitude, longitude, maxDistanceKm);
        return ResponseEntity.ok(serviceCatalogQueryService.search(request, pageable)
                .map(mapper::toResponse));
    }
}
