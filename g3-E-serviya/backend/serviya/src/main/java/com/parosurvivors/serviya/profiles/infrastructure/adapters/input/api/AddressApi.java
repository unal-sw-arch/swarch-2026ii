package com.parosurvivors.serviya.profiles.infrastructure.adapters.input.api;

import com.parosurvivors.serviya.profiles.infrastructure.dto.form.CreateAddressForm;
import com.parosurvivors.serviya.profiles.infrastructure.dto.form.UpdateAddressForm;
import com.parosurvivors.serviya.profiles.infrastructure.dto.form.VerifyAddressForm;
import com.parosurvivors.serviya.profiles.infrastructure.dto.response.AddressResponse;
import com.parosurvivors.serviya.profiles.infrastructure.dto.response.AddressVerificationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

import java.util.List;

/**
 * Documentacion OpenAPI/Swagger de las direcciones del usuario (modulo 2, seccion 4). RF-009.
 * Ver estructura-endpoints.md. Convencion: docs aqui; binding y @Parameter en el controller.
 */
@Tag(name = "Direcciones", description = "Direcciones del usuario autenticado y verificacion geoespacial")
@SecurityRequirement(name = "bearerAuth")
public interface AddressApi {

    @Operation(summary = "Listar las direcciones propias")
    @ApiResponse(responseCode = "200", description = "Lista de direcciones")
    ResponseEntity<List<AddressResponse>> getUserAddresses();

    @Operation(summary = "Crear una nueva direccion",
            description = "La linea de direccion se cifra con AES-256-GCM. RF-009.")
    @ApiResponse(responseCode = "201", description = "Direccion creada")
    ResponseEntity<AddressResponse> createAddress(CreateAddressForm form);

    @Operation(summary = "Actualizar parcialmente una direccion (PATCH)", description = "Verifica propiedad. RF-009.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Direccion actualizada"),
            @ApiResponse(responseCode = "403", description = "La direccion no pertenece al usuario")
    })
    ResponseEntity<AddressResponse> updateAddress(Long id, UpdateAddressForm form);

    @Operation(summary = "Eliminar una direccion",
            description = "Verifica propiedad y que no sea la principal activa. RF-009.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Direccion eliminada"),
            @ApiResponse(responseCode = "403", description = "La direccion no pertenece al usuario"),
            @ApiResponse(responseCode = "409", description = "Es la direccion principal activa")
    })
    ResponseEntity<Void> deleteAddress(Long id);

    @Operation(summary = "Verificar una direccion y geocodificarla",
            description = "Devuelve { valid, latitude, longitude }. RNF-019.")
    @ApiResponse(responseCode = "200", description = "Resultado de la verificacion")
    ResponseEntity<AddressVerificationResponse> verifyAddress(VerifyAddressForm form);
}
