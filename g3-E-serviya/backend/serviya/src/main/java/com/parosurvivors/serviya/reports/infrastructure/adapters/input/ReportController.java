package com.parosurvivors.serviya.reports.infrastructure.adapters.input;

import com.parosurvivors.serviya.reports.application.ports.input.ClientFeedbackReportServicePort;
import com.parosurvivors.serviya.reports.application.ports.input.ReportActionServicePort;
import com.parosurvivors.serviya.reports.application.ports.input.ReportServicePort;
import com.parosurvivors.serviya.reports.application.ports.input.RequestReportServicePort;
import com.parosurvivors.serviya.reports.application.ports.input.ServiceFeedbackReportServicePort;
import com.parosurvivors.serviya.reports.infrastructure.adapters.input.api.ReportApi;
import com.parosurvivors.serviya.reports.infrastructure.dto.form.CreateClientFeedbackReportForm;
import com.parosurvivors.serviya.reports.infrastructure.dto.form.CreateRequestReportForm;
import com.parosurvivors.serviya.reports.infrastructure.dto.form.CreateServiceFeedbackReportForm;
import com.parosurvivors.serviya.reports.infrastructure.dto.response.ClientFeedbackReportResponse;
import com.parosurvivors.serviya.reports.infrastructure.dto.response.ReportActionResponse;
import com.parosurvivors.serviya.reports.infrastructure.dto.response.ReportDetailResponse;
import com.parosurvivors.serviya.reports.infrastructure.dto.response.ReportResponse;
import com.parosurvivors.serviya.reports.infrastructure.dto.response.RequestReportResponse;
import com.parosurvivors.serviya.reports.infrastructure.dto.response.ServiceFeedbackReportResponse;
import com.parosurvivors.serviya.reports.infrastructure.mappers.ReportWebMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.parosurvivors.serviya.shared.ratelimit.RateLimit;
import com.parosurvivors.serviya.shared.security.CurrentUser;
import com.parosurvivors.serviya.shared.exceptions.UnauthorizedException;
import java.util.List;

/**
 * Adaptador de entrada (REST) de reportes. Placeholder funcional; documentacion en {@link ReportApi}.
 * Mapea Form->Command y dominio/Result->Response via {@link ReportWebMapper}.
 */
@RestController
@RequiredArgsConstructor
public class ReportController implements ReportApi {

    private final ReportServicePort reportService;
    private final RequestReportServicePort requestReportService;
    private final ServiceFeedbackReportServicePort serviceFeedbackReportService;
    private final ClientFeedbackReportServicePort clientFeedbackReportService;
    private final ReportActionServicePort reportActionService;
    private final ReportWebMapper mapper;

    @Override
    @PostMapping("/api/v1/reports/requests")
    @RateLimit("report-create")
    public ResponseEntity<RequestReportResponse> createRequestReport(
            @Valid @RequestBody CreateRequestReportForm form) {
        RequestReportResponse response = mapper.toResponse(
                requestReportService.createReport(new com.parosurvivors.serviya.reports.application.dto.command.CreateRequestReportCommand(
                        currentUserId(),
                        resolveCategory(form.category(), form.customCategory()),
                        form.reason(),
                        form.requestId())));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Override
    @PostMapping("/api/v1/reports/service-feedback")
    @RateLimit("report-create")
    public ResponseEntity<ServiceFeedbackReportResponse> createServiceFeedbackReport(
            @Valid @RequestBody CreateServiceFeedbackReportForm form) {
        ServiceFeedbackReportResponse response = mapper.toResponse(
                serviceFeedbackReportService.createReport(new com.parosurvivors.serviya.reports.application.dto.command.CreateServiceFeedbackReportCommand(
                        currentUserId(),
                        form.reportedUserId(),
                        resolveCategory(form.category(), form.customCategory()),
                        form.reason(),
                        form.serviceFeedbackId())));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Override
    @PostMapping("/api/v1/reports/client-feedback")
    @RateLimit("report-create")
    public ResponseEntity<ClientFeedbackReportResponse> createClientFeedbackReport(
            @Valid @RequestBody CreateClientFeedbackReportForm form) {
        ClientFeedbackReportResponse response = mapper.toResponse(
                clientFeedbackReportService.createReport(new com.parosurvivors.serviya.reports.application.dto.command.CreateClientFeedbackReportCommand(
                        currentUserId(),
                        form.reportedUserId(),
                        resolveCategory(form.category(), form.customCategory()),
                        form.reason(),
                        form.clientFeedbackId())));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Override
    @GetMapping("/api/v1/reports")
    public ResponseEntity<Page<ReportResponse>> getReports(@RequestParam(required = false) String type,
                                                          @RequestParam(required = false) String category,
                                                          @RequestParam(required = false) String status,
                                                          Pageable pageable) {
        return ResponseEntity.ok(reportService.getReports(type, category, status, pageable)
                .map(mapper::toResponse));
    }

    @Override
    @GetMapping("/api/v1/reports/{id}")
    public ResponseEntity<ReportDetailResponse> getReportDetail(@PathVariable Long id) {
        return ResponseEntity.ok(mapper.toResponse(reportService.getReportDetail(id)));
    }

    @Override
    @GetMapping("/api/v1/reports/{id}/actions")
    public ResponseEntity<List<ReportActionResponse>> getReportActions(@PathVariable Long id) {
        return ResponseEntity.ok(mapper.toActionResponses(reportActionService.getActionsByReport(id)));
    }

    @Override
    @GetMapping("/api/v1/users/{id}/reports/received")
    public ResponseEntity<List<ReportResponse>> getReportsReceived(@PathVariable Long id) {
        assertSelfOrAdmin(id);
        return ResponseEntity.ok(mapper.toReportResponses(reportService.getReportsByReportedUser(id)));
    }

    @Override
    @GetMapping("/api/v1/users/{id}/reports/sent")
    public ResponseEntity<List<ReportResponse>> getReportsSent(@PathVariable Long id) {
        assertSelfOrAdmin(id);
        return ResponseEntity.ok(mapper.toReportResponses(reportService.getReportsByReporter(id)));
    }

    private Long currentUserId() {
        return CurrentUser.id();
    }

    /** Grano fino: solo el propio usuario o un admin pueden ver sus reportes (recibidos/enviados). */
    private void assertSelfOrAdmin(Long targetUserId) {
        if (!CurrentUser.isAdmin() && !targetUserId.equals(CurrentUser.id())) {
            throw new UnauthorizedException("Solo puedes consultar tus propios reportes");
        }
    }

    private String resolveCategory(String category, String customCategory) {
        if ("Otra".equalsIgnoreCase(category) || "Otro".equalsIgnoreCase(category)) {
            return (customCategory == null || customCategory.isBlank()) ? "Otra" : customCategory.trim();
        }
        return category;
    }
}
