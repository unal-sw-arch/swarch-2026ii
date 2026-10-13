package com.parosurvivors.serviya.admin.infrastructure.dto.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Entrada web (Form) con el motivo de una suspensión de cuenta (RF-063). El motivo lo escribe el admin
 * y viaja al usuario baneado en la notificación por doble canal. El userId va en el path y el adminId
 * se extrae del JWT.
 *
 * <p>En el baneo directo (POST /api/v1/admin/users/{id}/ban) el cuerpo es obligatorio. En el baneo desde
 * un reporte (POST /api/v1/reports/{id}/actions/ban) el cuerpo es opcional: si se omite, el servicio de
 * moderación cae a un motivo derivado de la categoría del reporte.</p>
 */
@Schema(description = "Motivo de la suspensión, comunicado al usuario baneado")
public record BanUserForm(
        @NotBlank @Size(max = 500) String reason) {
}
