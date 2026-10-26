package com.parosurvivors.serviya.services.infrastructure.adapters.input;


import com.parosurvivors.serviya.metrics.domain.ServiceMetrics;
import com.parosurvivors.serviya.profiles.application.ports.input.UserProfileServicePort;
import com.parosurvivors.serviya.profiles.domain.UserProfile;
import com.parosurvivors.serviya.services.application.dto.command.UpdateServiceCommand;
import com.parosurvivors.serviya.services.application.dto.query.SearchServiceQuery;
import com.parosurvivors.serviya.services.application.ports.input.MarketplaceServicePort;
import com.parosurvivors.serviya.services.application.services.ServicePhotoStorageService;
import com.parosurvivors.serviya.services.domain.Service;
import com.parosurvivors.serviya.services.infrastructure.adapters.input.api.ServiceApi;
import com.parosurvivors.serviya.services.infrastructure.dto.form.CreateServiceForm;
import com.parosurvivors.serviya.services.infrastructure.dto.form.UpdateServiceForm;
import com.parosurvivors.serviya.services.infrastructure.dto.response.ServiceDetailResponse;
import com.parosurvivors.serviya.services.infrastructure.dto.response.ServiceResponse;
import com.parosurvivors.serviya.services.infrastructure.mappers.ServiceWebMapper;
import com.parosurvivors.serviya.services.domain.Service;

import com.parosurvivors.serviya.shared.ratelimit.RateLimit;
import com.parosurvivors.serviya.shared.security.CurrentUser;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Adaptador de entrada (REST) de servicios. Placeholder funcional: enruta, mapea Form->Command y
 * dominio->Response (ServiceWebMapper) y delega en {@link MarketplaceServicePort}. La documentacion
 * vive en {@link ServiceApi}. Cubre la parte de gestion de MarketplaceService; los servicios del
 * modulo-3 (busqueda, categorias, horario) aun no tienen puerto (ver NOTAS.txt).
 */
@RestController
@RequiredArgsConstructor
public class ServiceController implements ServiceApi {

    private final MarketplaceServicePort marketplaceService;
    private final ServiceWebMapper mapper;
    private final UserProfileServicePort userProfileService;
    private final ServicePhotoStorageService photoStorageService;

    @Override
    @PostMapping(value = "/api/v1/services", consumes = {"multipart/form-data"})
    @RateLimit("service-write")
    public ResponseEntity<ServiceResponse> create(
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "priceHourly", required = false) String priceHourly,
            @RequestParam(value = "categoryId", required = false) String categoryId,
            @RequestParam(value = "averageDurationMinutes", required = false) String averageDurationMinutes,
            @RequestParam(value = "operationRadiusKm", required = false) String operationRadiusKm,
            @RequestParam(value = "photos", required = false) List<MultipartFile> photos) {

        CreateServiceForm form = new CreateServiceForm(
                title,
                description,
                List.of(),
                parseDecimal(priceHourly),
                parseLong(categoryId),
                parseInteger(averageDurationMinutes),
                parseDecimal(operationRadiusKm),
                currentOffererId());

        Service createdService = marketplaceService.create(mapper.toCommand(form, currentOffererId()));
        if (photos != null && !photos.isEmpty()) {
            List<String> savedPaths = photoStorageService.storePhotos(createdService.getId(), photos);
            createdService = marketplaceService.update(new UpdateServiceCommand(
                    createdService.getId(),
                    null,
                    null,
                    savedPaths,
                    null,
                    null,
                    null,
                    null), CurrentUser.id(), CurrentUser.isAdmin());
        }

        ServiceResponse response = mapper.toResponse(createdService);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Override
    @GetMapping("/api/v1/services/{id}")
    public ResponseEntity<ServiceResponse> getById(
            @Parameter(description = "ID del servicio") @PathVariable Long id) {
        return marketplaceService.getById(id)
                .map(mapper::toResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Override
    @GetMapping("/api/v1/services/{id}/detail")
    public ResponseEntity<ServiceDetailResponse> getDetail(
            @Parameter(description = "ID del servicio") @PathVariable Long id) {
        return marketplaceService.getDetailById(id)
                .map(mapper::toDetailResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Override
    @GetMapping("/api/v1/services")
    public ResponseEntity<List<ServiceResponse>> getAll() {
        return ResponseEntity.ok(mapper.toResponses(marketplaceService.getAll()));
    }

    @Override
    @GetMapping("/api/v1/offerers/{offererId}/services")
    public ResponseEntity<List<ServiceDetailResponse>> getByOffererId(
            @Parameter(description = "ID del oferente") @PathVariable Long offererId) {
        return ResponseEntity.ok(marketplaceService.getByOffererId(offererId).stream().map(mapper::toDetailResponse).collect(Collectors.toList()));
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
            Pageable pageable
    ) {
        SearchServiceQuery criteria = SearchServiceQuery.builder()
                .name(name)
                .categoryId(categoryId)
                .offererId(offererId)
                .minPrice(minPrice)
                .maxPrice(maxPrice)
                .available(available)
                .minRating(minRating)
                .maxRating(maxRating)
                .latitude(latitude)
                .longitude(longitude)
                .maxDistanceKm(maxDistanceKm)
                .build();
        Page<Service> servicePage = marketplaceService.search(criteria, pageable);
        Map<Long, ServiceMetrics> metricsMap = marketplaceService.getMetricsForServices(
                servicePage.getContent().stream().map(Service::getId).collect(Collectors.toList()));

        Set<Long> offererIds = servicePage.getContent().stream()
                .map(Service::getOffererId).collect(Collectors.toSet());
        Map<Long, String> offererNames = new HashMap<>();
        for (Long oid : offererIds) {
            try {
                offererNames.put(oid, userProfileService.getProfileInfo(oid).getFullName());
            } catch (Exception e) {
                offererNames.put(oid, "Oferente");
            }
        }

        Page<ServiceResponse> page = servicePage.map(service -> {
            ServiceResponse r = mapper.toResponse(service, metricsMap.get(service.getId()));
            return withOffererName(r, offererNames.getOrDefault(service.getOffererId(), "Oferente"));
        });
        return ResponseEntity.ok(page);
    }

    @Override
    @PatchMapping(value = "/api/v1/services/{id}", consumes = {"multipart/form-data"})
    @RateLimit("service-write")
    public ResponseEntity<ServiceResponse> update(
            @Parameter(description = "ID del servicio") @PathVariable Long id,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "priceHourly", required = false) String priceHourly,
            @RequestParam(value = "categoryId", required = false) String categoryId,
            @RequestParam(value = "averageDurationMinutes", required = false) String averageDurationMinutes,
            @RequestParam(value = "operationRadiusKm", required = false) String operationRadiusKm,
            @RequestParam(value = "photos", required = false) List<MultipartFile> photos,
            @RequestParam(value = "existingPhotos", required = false) List<String> existingPhotos,
            @RequestParam(value = "removedPhotos", required = false) List<String> removedPhotos) {

        UpdateServiceForm form = new UpdateServiceForm(
                title,
                description,
                List.of(),
                parseDecimal(priceHourly),
                parseLong(categoryId),
                parseInteger(averageDurationMinutes),
                parseDecimal(operationRadiusKm));

        Service updatedService = marketplaceService.update(mapper.toCommand(form, id), CurrentUser.id(), CurrentUser.isAdmin());
        List<String> retainedPhotos = existingPhotos == null ? List.of() : existingPhotos.stream().filter(p -> p != null && !p.isBlank()).toList();
        if (removedPhotos != null && !removedPhotos.isEmpty()) {
            photoStorageService.deletePhotos(removedPhotos);
        }
        if (photos != null && !photos.isEmpty()) {
            if (retainedPhotos.size() + photos.size() > 15) {
                throw new IllegalArgumentException("Máximo 15 fotos por servicio");
            }
            List<String> savedPaths = photoStorageService.storePhotos(id, photos);
            List<String> finalPhotos = new java.util.ArrayList<>(retainedPhotos);
            finalPhotos.addAll(savedPaths);
            updatedService = marketplaceService.update(new UpdateServiceCommand(
                    id,
                    null,
                    null,
                    finalPhotos,
                    null,
                    null,
                    null,
                    null), CurrentUser.id(), CurrentUser.isAdmin());
        } else if (!retainedPhotos.isEmpty() || (removedPhotos != null && !removedPhotos.isEmpty())) {
            updatedService = marketplaceService.update(new UpdateServiceCommand(
                    id,
                    null,
                    null,
                    retainedPhotos,
                    null,
                    null,
                    null,
                    null), CurrentUser.id(), CurrentUser.isAdmin());
        }

        return ResponseEntity.ok(mapper.toResponse(updatedService));
    }

    private BigDecimal parseDecimal(String value) {
        if (value == null || value.isBlank() || value.equalsIgnoreCase("NaN")) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Long parseLong(String value) {
        if (value == null || value.isBlank() || value.equalsIgnoreCase("NaN")) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Integer parseInteger(String value) {
        if (value == null || value.isBlank() || value.equalsIgnoreCase("NaN")) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    @Override
    @DeleteMapping("/api/v1/services/{id}")
    public ResponseEntity<Void> delete(
            @Parameter(description = "ID del servicio") @PathVariable Long id) {
        marketplaceService.delete(id, CurrentUser.id(), CurrentUser.isAdmin());
        return ResponseEntity.noContent().build();
    }

    @Override
    @PatchMapping("/api/v1/services/{id}/soft-delete")
    public ResponseEntity<Void> softDelete(
            @Parameter(description = "ID del servicio") @PathVariable Long id) {
        marketplaceService.softDelete(id, CurrentUser.id(), CurrentUser.isAdmin());
        return ResponseEntity.noContent().build();
    }

    @Override
    @PostMapping("/api/v1/services/{id}/activate")
    public ResponseEntity<Void> activate(
            @Parameter(description = "ID del servicio") @PathVariable Long id) {
        marketplaceService.activate(id, CurrentUser.id(), CurrentUser.isAdmin());
        return ResponseEntity.noContent().build();
    }

    @Override
    @PostMapping("/api/v1/services/{id}/deactivate")
    public ResponseEntity<Void> deactivate(
            @Parameter(description = "ID del servicio") @PathVariable Long id) {
        marketplaceService.deactivate(id, CurrentUser.id(), CurrentUser.isAdmin());
        return ResponseEntity.noContent().build();
    }

    private Long currentOffererId() {
        return CurrentUser.id();
    }

    private ServiceResponse withOffererName(ServiceResponse r, String name) {
        return new ServiceResponse(
                r.id(), r.offererId(), name,
                r.title(), r.description(), r.photos(), r.priceHourly(),
                r.categoryId(), r.averageDurationMinutes(), r.active(),
                r.operationRadiusKm(), r.createdAt(), r.updatedAt(), r.deletedAt(),
                r.averageRating(), r.totalRatings(), r.totalComments());
    }
}
