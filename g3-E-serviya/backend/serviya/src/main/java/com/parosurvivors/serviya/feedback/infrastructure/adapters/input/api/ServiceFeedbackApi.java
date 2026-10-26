package com.parosurvivors.serviya.feedback.infrastructure.adapters.input.api;

import com.parosurvivors.serviya.feedback.infrastructure.dto.form.SubmitServiceFeedbackForm;
import com.parosurvivors.serviya.feedback.infrastructure.dto.response.ServiceFeedbackResponse;
import com.parosurvivors.serviya.feedback.infrastructure.dto.response.ServiceFeedbackTagResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

import java.util.List;

/**
 * Documentacion OpenAPI/Swagger del feedback del cliente al servicio (modulo 5, seccion 12).
 * Ver estructura-endpoints.md. Convencion: docs aqui; binding y @Parameter en el controller.
 */
@Tag(name = "Feedback de servicio", description = "Calificaciones y resenas que el cliente deja a un servicio")
public interface ServiceFeedbackApi {

    @Operation(summary = "Enviar feedback de servicio (rating y/o resena)",
            description = "Un solo POST agrupa rating + comentario (cualquiera puede venir null). RF-041, RF-045.")
    @ApiResponse(responseCode = "204", description = "Feedback registrado")
    ResponseEntity<Void> submitServiceFeedback(Long id, SubmitServiceFeedbackForm form);

    @Operation(summary = "Obtener el feedback de una solicitud", description = "Rating + resena emparejados.")
    @ApiResponse(responseCode = "200", description = "Feedback de la solicitud")
    ResponseEntity<ServiceFeedbackResponse> getServiceFeedback(Long id);

    @Operation(summary = "Existe feedback de servicio para la solicitud",
            description = "Devuelve un booleano; evita depender de un 404 para saber si ya se califico.")
    @ApiResponse(responseCode = "200", description = "true si la solicitud ya tiene feedback de servicio")
    ResponseEntity<Boolean> serviceFeedbackExists(Long id);

    @Operation(summary = "Listar el feedback recibido por un servicio", description = "RF-040, RF-046.")
    @ApiResponse(responseCode = "200", description = "Pagina de feedback")
    ResponseEntity<Page<ServiceFeedbackResponse>> getServiceFeedbackList(Long id, Pageable pageable);

    @Operation(summary = "Listar el feedback de servicio dejado por un cliente")
    @ApiResponse(responseCode = "200", description = "Pagina de feedback del cliente")
    ResponseEntity<Page<ServiceFeedbackResponse>> getServiceFeedbackByClient(Long id, Pageable pageable);

    @Operation(summary = "Obtener el catalogo de tags de resenas de servicio")
    @ApiResponse(responseCode = "200", description = "Catalogo de tags")
    ResponseEntity<List<ServiceFeedbackTagResponse>> getCatalog();
}
