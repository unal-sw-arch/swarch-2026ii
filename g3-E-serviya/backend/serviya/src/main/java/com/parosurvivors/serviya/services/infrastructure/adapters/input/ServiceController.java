package com.parosurvivors.serviya.services.infrastructure.adapters.input;


import com.parosurvivors.serviya.services.application.dto.command.UpdateServiceCommand;
import com.parosurvivors.serviya.services.application.ports.input.MarketplaceServicePort;
import com.parosurvivors.serviya.services.application.services.ServicePhotoStorageService;
import com.parosurvivors.serviya.services.domain.Service;
import com.parosurvivors.serviya.services.infrastructure.adapters.input.api.ServiceApi;
import com.parosurvivors.serviya.services.infrastructure.dto.form.CreateServiceForm;
import com.parosurvivors.serviya.services.infrastructure.dto.form.UpdateServiceForm;
import com.parosurvivors.serviya.services.infrastructure.dto.response.ServiceResponse;
import com.parosurvivors.serviya.services.infrastructure.mappers.ServiceWebMapper;

import com.parosurvivors.serviya.shared.ratelimit.RateLimit;
import com.parosurvivors.serviya.shared.security.CurrentUser;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;

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
    @GetMapping("/api/v1/services")
    public ResponseEntity<List<ServiceResponse>> getAll() {
        return ResponseEntity.ok(mapper.toResponses(marketplaceService.getAll()));
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
}
