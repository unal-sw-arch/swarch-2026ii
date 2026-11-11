package com.parosurvivors.serviya.admin.application.services;

import com.parosurvivors.serviya.admin.application.dto.command.CreateUserByAdminCommand;
import com.parosurvivors.serviya.admin.application.dto.command.UpdateUserByAdminCommand;
import com.parosurvivors.serviya.admin.application.dto.query.AdminFeedbackSearchQuery;
import com.parosurvivors.serviya.admin.application.dto.result.AdminFeedbackSearchResult;
import com.parosurvivors.serviya.admin.application.dto.result.AdminRequestDetailResult;
import com.parosurvivors.serviya.admin.application.dto.result.AdminUserSummaryResult;
import com.parosurvivors.serviya.admin.application.dto.result.UserAdminDetailResult;
import com.parosurvivors.serviya.admin.application.ports.input.AdminServicePort;
import com.parosurvivors.serviya.feedback.api.FeedbackQueryApi;
import com.parosurvivors.serviya.feedback.api.dto.ClientFeedbackSnapshot;
import com.parosurvivors.serviya.feedback.api.dto.ServiceFeedbackSnapshot;
import com.parosurvivors.serviya.metrics.api.MetricsQueryApi;
import com.parosurvivors.serviya.metrics.api.dto.ClientMetricsSnapshot;
import com.parosurvivors.serviya.metrics.api.dto.OffererMetricsSnapshot;
import com.parosurvivors.serviya.notifications.api.NotificationCommand;
import com.parosurvivors.serviya.notifications.api.NotificationCommandApi;
import com.parosurvivors.serviya.profiles.api.ProfileCommandApi;
import com.parosurvivors.serviya.profiles.api.ProfileQueryApi;
import com.parosurvivors.serviya.profiles.api.dto.AddressSnapshot;
import com.parosurvivors.serviya.profiles.api.dto.ProfileSnapshot;
import com.parosurvivors.serviya.profiles.api.dto.UpdateProfileCommand;
import com.parosurvivors.serviya.reports.api.ReportQueryApi;
import com.parosurvivors.serviya.requests.api.RequestCommandApi;
import com.parosurvivors.serviya.requests.api.RequestQueryApi;
import com.parosurvivors.serviya.requests.api.dto.RequestDetailSnapshot;
import com.parosurvivors.serviya.services.api.ServiceCommandApi;
import com.parosurvivors.serviya.services.api.ServiceQueryApi;
import com.parosurvivors.serviya.services.api.dto.CategorySnapshot;
import com.parosurvivors.serviya.services.api.dto.ServiceSnapshot;
import com.parosurvivors.serviya.shared.contract.PageResult;
import com.parosurvivors.serviya.shared.contract.Roles;
import com.parosurvivors.serviya.shared.exceptions.InvalidStateException;
import com.parosurvivors.serviya.shared.exceptions.ResourceNotFoundException;
import com.parosurvivors.serviya.users.api.UserCommandApi;
import com.parosurvivors.serviya.users.api.UserQueryApi;
import com.parosurvivors.serviya.users.api.dto.CreateUserAccountCommand;
import com.parosurvivors.serviya.users.api.dto.RoleSnapshot;
import com.parosurvivors.serviya.users.api.dto.UserSearchCriteria;
import com.parosurvivors.serviya.users.api.dto.UserSnapshot;
import com.parosurvivors.serviya.users.api.dto.UserSummary;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Administracion de usuarios del panel admin (contexto backoffice). Orquesta con las APIs publicas de
 * users, profiles, metrics, reports, requests, services, feedback y notifications; nunca con sus
 * repositorios, dominio o puertos internos. El gate de rol ADMIN vive en el endpoint (Spring Security);
 * aqui no se repite. Ver estructura-servicios.docx (modulo 9).
 */
@Component
@RequiredArgsConstructor
public class AdminService implements AdminServicePort {

    private final UserQueryApi userQueryApi;
    private final UserCommandApi userCommandApi;
    private final ProfileQueryApi profileQueryApi;
    private final ProfileCommandApi profileCommandApi;
    private final MetricsQueryApi metricsQueryApi;
    private final ReportQueryApi reportQueryApi;
    private final RequestQueryApi requestQueryApi;
    private final RequestCommandApi requestCommandApi;
    private final ServiceQueryApi serviceQueryApi;
    private final ServiceCommandApi serviceCommandApi;
    private final FeedbackQueryApi feedbackQueryApi;
    private final NotificationCommandApi notificationCommandApi;
    /** B5: anti auto-accion y guard de ultimo admin activo. */
    private final AdminActionGuard adminActionGuard;

    @Override
    public AdminUserSummaryResult createUserByAdmin(CreateUserByAdminCommand command) {
        // Llamador expuesto del mecanismo de creacion: admite CUALQUIER rol, incluido ADMIN. Sin restriccion
        // de politica de rol aqui. El consentimiento se da por aceptado al crear desde el panel admin; la
        // unicidad de email la valida createUserAccount. Devuelve el read-model de listado (sin foto aun).
        UserSnapshot created = userCommandApi.createUserAccount(new CreateUserAccountCommand(
                command.email(), command.password(), command.fullName(), command.role(),
                command.documentType(), command.documentNumber(), command.phone(), true,
                // El alta desde el panel admin no captura direccion; el usuario la agrega luego.
                null, null, null, null));
        return new AdminUserSummaryResult(created.id(), created.email(), command.fullName(), null,
                created.banned(), null, created.createdAt());
    }

    @Override
    @Transactional
    public void grantRoleByAdmin(Long adminId, Long userId, String roleName) {
        UserSnapshot user = requireUser(userId);
        if (user.deleted()) {
            throw new InvalidStateException("No se puede asignar un rol a un usuario eliminado");
        }
        String role = normalizeRole(roleName);
        userCommandApi.assignRole(userId, role);
        // Se avisa al usuario de la concesión del rol (canal interno por defecto del modulo).
        notificationCommandApi.notify(new NotificationCommand(
                userId,
                "role_granted",
                "Se te concedió un rol",
                "Un administrador te concedió el rol " + role + " en tu cuenta.",
                "USER",
                userId,
                null,
                Map.of()));
    }

    /**
     * RF-066: retira un rol a un usuario, con la cascada conservadora que evita dejar datos huerfanos.
     *
     * <p><b>Decision de diseño documentada</b>: el documento no define que ocurre con los servicios y
     * solicitudes activas al retirar un rol. Se aplica el mismo criterio que en la eliminacion de cuenta
     * (RF-008), acotado al rol retirado:
     * <ul>
     *   <li>OFFERER → se desactivan sus servicios y se cancelan las solicitudes donde actuaba como
     *       oferente (las que tenga como cliente NO se tocan: conserva ese rol).</li>
     *   <li>CLIENT → se cancelan las solicitudes donde actuaba como cliente.</li>
     *   <li>ADMIN → no arrastra datos operativos: solo se retira el rol.</li>
     * </ul>
     * cancelRequest ya avisa a cada contraparte y publica el evento de metricas, asi que no se duplica
     * esa logica aqui. Todo en una sola transaccion.</p>
     */
    @Override
    @Transactional
    public void revokeRoleByAdmin(Long adminId, Long userId, String roleName) {
        String role = normalizeRole(roleName);
        requireUser(userId); // 404 si no existe

        // B5: retirar el rol ADMIN a uno mismo o al ultimo admin activo provocaria lockout.
        if (Roles.ADMIN.equals(role)) {
            adminActionGuard.requireNotSelf(adminId, userId);
            adminActionGuard.requireAnotherActiveAdmin(userId);
        }

        switch (role) {
            case Roles.OFFERER -> {
                serviceCommandApi.deactivateAllByOfferer(userId);
                requestCommandApi.cancelActiveRequestsForRole(userId, true);
            }
            case Roles.CLIENT -> requestCommandApi.cancelActiveRequestsForRole(userId, false);
            case Roles.ADMIN -> {
                // Sin datos operativos asociados.
            }
            default -> throw new InvalidStateException("Invalid role: " + roleName);
        }

        userCommandApi.revokeRole(userId, role);

        notificationCommandApi.notify(new NotificationCommand(
                userId,
                "role_revoked",
                "Se retiro un rol de tu cuenta",
                "Un administrador retiro el rol " + role + " de tu cuenta.",
                "USER",
                userId,
                null,
                Map.of()));
    }

    /**
     * RF-068: edicion de un usuario por el administrador. Solo se tocan los campos enviados (PATCH
     * parcial). Los datos PII (telefono) se cifran al persistir igual que en el resto del sistema, y el
     * documento sigue siendo inmutable (no viaja en el command), como en RF-006.
     */
    @Override
    @Transactional
    public void updateUserByAdmin(Long adminId, Long userId, UpdateUserByAdminCommand command) {
        requireUser(userId); // 404 si no existe

        if (command.email() != null) {
            userCommandApi.changeEmail(userId, command.email());
        }
        // Reutiliza el mismo caso de uso de RF-006: filtro de palabras, PATCH parcial y cifrado de PII
        // viven ahi, no se duplican aqui.
        profileCommandApi.patchProfile(new UpdateProfileCommand(
                userId, command.fullName(), command.phone(), command.photoUrl(), command.description()));
    }

    @Override
    public Page<AdminUserSummaryResult> searchUsers(UserSearchCriteria criteria, Pageable pageable) {
        PageResult<UserSummary> result = userQueryApi.search(
                criteria, pageable.getPageNumber(), pageable.getPageSize());

        List<Long> userIds = result.content().stream().map(UserSummary::id).toList();
        Map<Long, ProfileSnapshot> profiles = profileQueryApi.findProfiles(userIds).stream()
                .collect(Collectors.toMap(ProfileSnapshot::userId, Function.identity()));

        List<AdminUserSummaryResult> content = result.content().stream()
                .map(summary -> {
                    ProfileSnapshot profile = profiles.get(summary.id());
                    return new AdminUserSummaryResult(
                            summary.id(),
                            summary.email(),
                            profile == null ? null : profile.fullName(),
                            profile == null ? null : profile.profilePhotoUrl(),
                            summary.banned(),
                            summary.deletedAt(),
                            summary.createdAt());
                })
                .toList();
        return new PageImpl<>(content, pageable, result.totalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public UserAdminDetailResult getUserAdminDetail(Long userId) {
        UserSnapshot user = requireUser(userId);
        List<String> roles = userQueryApi.getRoles(userId).stream()
                .map(RoleSnapshot::name)
                .toList();
        ProfileSnapshot profile = profileQueryApi.findProfile(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Perfil no encontrado para userId: " + userId));
        // Metricas de comportamiento leidas de metrics.api (fuente unica). getMainMetrics devuelve
        // ceros si el usuario no tiene ese rol, asi que ambos bloques vienen siempre presentes.
        OffererMetricsSnapshot offererMetrics = metricsQueryApi.getOffererMainMetrics(userId);
        ClientMetricsSnapshot clientMetrics = metricsQueryApi.getClientMainMetrics(userId);
        // Reportes: moderacion, no metricas; conteo en reports.api.
        int reportsReceived = (int) reportQueryApi.countByReportedUser(userId);
        int reportsSent = (int) reportQueryApi.countByReporter(userId);
        int activeRequests = (int) requestQueryApi.countActiveClientRequests(userId);
        return new UserAdminDetailResult(
                user.id(), user.email(), user.banned(), user.deletedAt(), user.createdAt(), roles,
                profile.fullName(), profile.profilePhotoUrl(), profile.documentType(),
                profile.documentNumber(), profile.phoneNumber(), profile.bio(),
                profile.profileType(),
                offererMetrics, clientMetrics, activeRequests, reportsReceived, reportsSent);
    }

    @Override
    @Transactional(readOnly = true)
    public AdminRequestDetailResult getRequestDetailForAdmin(Long requestId) {
        // Vista administrativa (ambas partes). El gate de rol ADMIN vive en el endpoint (Spring Security).
        RequestDetailSnapshot request = requestQueryApi.findDetail(requestId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Solicitud no encontrada: " + requestId));

        ServiceSnapshot service = request.serviceId() == null ? null
                : serviceQueryApi.findById(request.serviceId()).orElse(null);
        CategorySnapshot category = service == null || service.categoryId() == null ? null
                : serviceQueryApi.findCategory(service.categoryId()).orElse(null);
        ProfileSnapshot client = request.clientId() == null ? null
                : profileQueryApi.findProfile(request.clientId()).orElse(null);
        ProfileSnapshot offerer = request.offererId() == null ? null
                : profileQueryApi.findProfile(request.offererId()).orElse(null);
        AddressSnapshot address = request.addressId() == null ? null
                : profileQueryApi.findAddress(request.addressId()).orElse(null);

        return new AdminRequestDetailResult(
                request.id(),
                request.status(),
                request.scheduledDate(),
                request.requestedPrice(),
                request.createdAt(),
                request.completedAt(),
                request.updatedStatusAt(),
                request.updatedBy(),
                request.previousRequestId(),
                request.serviceId(),
                service == null ? null : service.title(),
                category == null ? null : category.name(),
                service == null ? null : service.priceHourly(),
                service == null ? null : service.averageDurationMinutes(),
                request.clientId(),
                client == null ? null : client.fullName(),
                client == null ? null : client.profilePhotoUrl(),
                request.offererId(),
                offerer == null ? null : offerer.fullName(),
                offerer == null ? null : offerer.profilePhotoUrl(),
                request.addressId(),
                address == null ? null : address.addressLine(),
                address == null ? null : address.city(),
                address == null ? null : address.latitude(),
                address == null ? null : address.longitude());
    }

    @Override
    public void banUser(Long adminId, Long userId, String reason) {
        adminActionGuard.requireNotSelf(adminId, userId);
        adminActionGuard.requireAnotherActiveAdmin(userId);
        userCommandApi.banUser(userId, reason);
    }

    @Override
    public void unbanUser(Long adminId, Long userId) {
        userCommandApi.unbanUser(userId);
    }

    @Override
    public void deleteUser(Long adminId, Long userId) {
        adminActionGuard.requireNotSelf(adminId, userId);
        adminActionGuard.requireAnotherActiveAdmin(userId);
        userCommandApi.deleteUser(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AdminFeedbackSearchResult> searchFeedback(AdminFeedbackSearchQuery query, Pageable pageable) {
        PageResult<ServiceFeedbackSnapshot> servicePage = feedbackQueryApi.searchServiceFeedback(
                query.serviceId(), query.clientId(), query.keyword(),
                query.ratingMin(), query.ratingMax(),
                pageable.getPageNumber(), pageable.getPageSize());
        PageResult<ClientFeedbackSnapshot> clientPage = feedbackQueryApi.searchClientFeedback(
                query.clientId(), query.offererId(), query.keyword(),
                query.ratingMin(), query.ratingMax(),
                pageable.getPageNumber(), pageable.getPageSize());

        List<AdminFeedbackSearchResult> combined = Stream.concat(
                        servicePage.content().stream().map(f -> new AdminFeedbackSearchResult(
                                "SERVICE", f.id(), f.requestId(),
                                f.clientId(), f.serviceId(),
                                f.rating(), f.comment(), f.createdAt())),
                        clientPage.content().stream().map(f -> new AdminFeedbackSearchResult(
                                "CLIENT", f.id(), f.requestId(),
                                f.offererId(), f.clientId(),
                                f.rating(), f.comment(), f.createdAt())))
                .sorted(Comparator.comparing(AdminFeedbackSearchResult::createdAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), combined.size());
        List<AdminFeedbackSearchResult> pageContent = start >= combined.size()
                ? List.of() : combined.subList(start, end);
        long totalElements = servicePage.totalElements() + clientPage.totalElements();
        return new PageImpl<>(pageContent, pageable, totalElements);
    }

    @Override
    public void deleteService(Long serviceId, Long adminId, boolean isAdmin) {
        serviceCommandApi.deleteServiceAsAdmin(serviceId, adminId);
    }

    private UserSnapshot requireUser(Long userId) {
        return userQueryApi.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found with id: " + userId));
    }

    private String normalizeRole(String roleName) {
        String role = roleName == null ? "" : roleName.strip().toUpperCase();
        if (!Roles.CLIENT.equals(role) && !Roles.OFFERER.equals(role) && !Roles.ADMIN.equals(role)) {
            throw new InvalidStateException("Invalid role: " + roleName);
        }
        return role;
    }
}
