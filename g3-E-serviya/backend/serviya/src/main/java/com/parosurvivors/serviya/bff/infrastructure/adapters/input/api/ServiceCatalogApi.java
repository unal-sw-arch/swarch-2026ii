package com.parosurvivors.serviya.bff.infrastructure.adapters.input.api;

import com.parosurvivors.serviya.bff.infrastructure.dto.response.ServiceDetailResponse;
import com.parosurvivors.serviya.bff.infrastructure.dto.response.ServiceResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;

/**
 * Documentacion OpenAPI de las lecturas compuestas del catalogo de servicios.
 */
@Tag(name = "Catalogo de servicios", description = "Busqueda y detalle compuestos por el BFF")
public interface ServiceCatalogApi {

    @Operation(summary = "Obtener el detalle completo de un servicio (incluye datos del oferente y categoria)")
    @ApiResponse(responseCode = "200", description = "Detalle del servicio")
    ResponseEntity<ServiceDetailResponse> getDetail(Long id);

    @Operation(summary = "Listar servicios de un oferente")
    @ApiResponse(responseCode = "200", description = "Lista de servicios del oferente")
    ResponseEntity<List<ServiceDetailResponse>> getByOffererId(Long offererId);

    @Operation(summary = "Buscar servicios con filtros y paginacion")
    @ApiResponse(responseCode = "200", description = "Pagina de servicios encontrados")
    ResponseEntity<Page<ServiceResponse>> search(
            String name, Long categoryId, Long offererId,
            BigDecimal minPrice, BigDecimal maxPrice, Boolean available,
            Double minRating, Double maxRating,
            Double latitude, Double longitude, Double maxDistanceKm,
            Pageable pageable);
}
