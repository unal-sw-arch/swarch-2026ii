package com.parosurvivors.serviya.admin.infrastructure.adapters.input.api;

import com.parosurvivors.serviya.admin.infrastructure.dto.form.BanUserForm;
import com.parosurvivors.serviya.admin.infrastructure.dto.form.RemoveFeedbackForm;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

/**
 * Documentacion OpenAPI/Swagger de las acciones de moderacion del admin (modulo 9, seccion 16/17).
 * Ver estructura-endpoints.md. Todas requieren rol ADMIN. Convencion: docs aqui; binding y @Parameter en el controller.
 */
@Tag(name = "Moderacion", description = "Acciones de un admin sobre reportes y contenido")
@SecurityRequirement(name = "bearerAuth")
public interface ModerationApi {

    @Operation(summary = "Advertir al usuario reportado", description = "RF-060, RF-071.")
    @ApiResponse(responseCode = "204", description = "Advertencia aplicada y reporte cerrado")
    ResponseEntity<Void> warnUser(Long id);

    @Operation(summary = "Banear al usuario reportado", description = "RF-069, RF-063. El motivo es opcional: "
            + "si el admin no lo indica, se deriva de la categoría del reporte. Se comunica al baneado por doble canal.")
    @ApiResponse(responseCode = "204", description = "Usuario baneado y reporte cerrado")
    ResponseEntity<Void> banUserFromReport(Long id, BanUserForm form);

    @Operation(summary = "Revertir el feedback reportado", description = "RF-049.")
    @ApiResponse(responseCode = "204", description = "Feedback revertido y reporte cerrado")
    ResponseEntity<Void> revertFeedback(Long id);

    @Operation(summary = "Cerrar el reporte sin penalizacion", description = "RF-059.")
    @ApiResponse(responseCode = "204", description = "Reporte cerrado")
    ResponseEntity<Void> closeReport(Long id);

    @Operation(summary = "Marcar la solicitud del reporte como no prestada", description = "RF-074.")
    @ApiResponse(responseCode = "204", description = "Solicitud marcada como no prestada y reporte cerrado")
    ResponseEntity<Void> markRequestAsNotProvided(Long id);

    @Operation(summary = "Eliminar feedback inapropiado no reportado",
            description = "Crea internamente un reporte y lo resuelve (auditoria). RF-049.")
    @ApiResponse(responseCode = "204", description = "Feedback eliminado con rastro de auditoria")
    ResponseEntity<Void> removeFeedbackDirectly(RemoveFeedbackForm form);
}
