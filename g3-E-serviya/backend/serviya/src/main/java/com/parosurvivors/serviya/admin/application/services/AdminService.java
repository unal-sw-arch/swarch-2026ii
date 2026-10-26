package com.parosurvivors.serviya.admin.application.services;

import com.parosurvivors.serviya.admin.application.dto.command.CreateUserByAdminCommand;
import com.parosurvivors.serviya.admin.application.dto.command.UpdateUserByAdminCommand;
import com.parosurvivors.serviya.profiles.application.dto.command.UpdateProfileCommand;
import com.parosurvivors.serviya.requests.application.ports.input.ServiceRequestCommandServicePort;
import com.parosurvivors.serviya.services.application.ports.input.MarketplaceServicePort;
import com.parosurvivors.serviya.users.application.dto.command.ChangeEmailCommand;
import com.parosurvivors.serviya.admin.application.dto.query.AdminFeedbackSearchQuery;
import com.parosurvivors.serviya.admin.application.dto.result.AdminFeedbackSearchResult;
import com.parosurvivors.serviya.admin.application.dto.result.UserAdminDetailResult;
import com.parosurvivors.serviya.admin.application.ports.input.AdminServicePort;
import com.parosurvivors.serviya.feedback.application.ports.output.ClientFeedbackPersistencePort;
import com.parosurvivors.serviya.feedback.application.ports.output.ServiceFeedbackPersistencePort;
import com.parosurvivors.serviya.feedback.domain.ClientFeedback;
import com.parosurvivors.serviya.feedback.domain.ServiceFeedback;
import com.parosurvivors.serviya.metrics.application.ports.input.ClientMetricsServicePort;
import com.parosurvivors.serviya.metrics.application.ports.input.OffererMetricsServicePort;
import com.parosurvivors.serviya.metrics.domain.ClientMetrics;
import com.parosurvivors.serviya.metrics.domain.OffererMetrics;
import com.parosurvivors.serviya.notifications.application.ports.input.NotificationServicePort;
import com.parosurvivors.serviya.profiles.application.ports.input.UserProfileServicePort;
import com.parosurvivors.serviya.profiles.domain.UserProfile;
import com.parosurvivors.serviya.reports.application.ports.input.ReportServicePort;
import com.parosurvivors.serviya.requests.application.dto.result.AdminRequestDetailResult;
import com.parosurvivors.serviya.requests.application.ports.input.ServiceRequestQueryServicePort;
import com.parosurvivors.serviya.services.application.ports.input.MarketplaceServicePort;
import com.parosurvivors.serviya.shared.exceptions.InvalidStateException;
import com.parosurvivors.serviya.users.application.dto.command.CreateUserAccountCommand;
import com.parosurvivors.serviya.users.application.dto.item.UserSummaryItem;
import com.parosurvivors.serviya.users.application.dto.query.SearchUsersQuery;
import com.parosurvivors.serviya.users.application.ports.input.UserCreationServicePort;
import com.parosurvivors.serviya.users.application.ports.input.UserDeletionServicePort;
import com.parosurvivors.serviya.users.application.ports.input.UserQueryServicePort;
import com.parosurvivors.serviya.users.application.ports.input.UserRoleServicePort;
import com.parosurvivors.serviya.users.application.ports.input.UserServicePort;
import com.parosurvivors.serviya.users.domain.RoleName;
import com.parosurvivors.serviya.users.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Administracion de usuarios del panel admin (contexto backoffice). Orquesta hacia los puertos de entrada
 * de los demas modulos (users, profiles, metrics, reports, requests), nunca sus repositorios. El gate de rol
 * ADMIN vive en el endpoint (Spring Security); aqui no se repite. Ver estructura-servicios.docx (modulo 9).
 */
@Component
@RequiredArgsConstructor
public class AdminService implements AdminServicePort {

    private final UserServicePort userServicePort;
    private final UserCreationServicePort userCreationServicePort;
    private final UserRoleServicePort userRoleServicePort;
    private final UserDeletionServicePort userDeletionServicePort;
    private final UserQueryServicePort userQueryServicePort;
    private final UserProfileServicePort userProfileServicePort;
    private final OffererMetricsServicePort offererMetricsServicePort;
    private final ClientMetricsServicePort clientMetricsServicePort;
    private final ReportServicePort reportServicePort;
    private final ServiceRequestQueryServicePort serviceRequestQueryServicePort;
    private final NotificationServicePort notificationServicePort;
    /** RF-066: la cascada conservadora al retirar un rol desactiva servicios y cancela solicitudes. */
    private final MarketplaceServicePort marketplaceServicePort;
    private final ServiceRequestCommandServicePort serviceRequestCommandServicePort;
    private final ServiceFeedbackPersistencePort serviceFeedbackPersistencePort;
    private final ClientFeedbackPersistencePort clientFeedbackPersistencePort;
    /** B5: anti auto-accion y guard de ultimo admin activo. */
    private final AdminActionGuard adminActionGuard;

    @Override
    public UserSummaryItem createUserByAdmin(CreateUserByAdminCommand command) {
        // Llamador expuesto del mecanismo de creacion: admite CUALQUIER rol, incluido ADMIN. Sin restriccion
        // de politica de rol aqui. El consentimiento se da por aceptado al crear desde el panel admin; la
        // unicidad de email la valida createUserAccount. Devuelve el read-model de listado (sin foto aun).
        CreateUserAccountCommand accountCommand = new CreateUserAccountCommand(
                command.email(), command.password(), command.fullName(), command.role(),
                command.documentType(), command.documentNumber(), command.phone(), true,
                // El alta desde el panel admin no captura direccion; el usuario la agrega luego.
                null, null, null, null);
        User created = userCreationServicePort.createUserAccount(accountCommand);
        return new UserSummaryItem(created.getId(), created.getEmail(), command.fullName(), null,
                created.getBanned(), created.getDeletedAt(), created.getCreatedAt());
    }

    @Override
    @Transactional
    public void grantRoleByAdmin(Long adminId, Long userId, String roleName) {
        User user = userQueryServicePort.getUserById(userId);
        if (user.isDeleted()) {
            throw new InvalidStateException("No se puede asignar un rol a un usuario eliminado");
        }
        // Cualquier rol , por nombre. La existencia del rol y el duplicado los valida assignRole.
        RoleName role = parseRole(roleName);
        userRoleServicePort.assignRole(userId, role);
        // Se avisa al usuario de la concesión del rol (canal interno).
        notificationServicePort.notify(
                userId,
                "role_granted",
                "Se te concedió un rol",
                "Un administrador te concedió el rol " + role.name() + " en tu cuenta.",
                "USER",
                userId,
                null,
                Map.of());
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
        RoleName role = parseRole(roleName);
        userQueryServicePort.getUserById(userId); // 404 si no existe

        // B5: retirar el rol ADMIN a uno mismo o al ultimo admin activo provocaria lockout.
        if (role == RoleName.ADMIN) {
            adminActionGuard.requireNotSelf(adminId, userId);
            adminActionGuard.requireAnotherActiveAdmin(userId);
        }

        switch (role) {
            case OFFERER -> {
                marketplaceServicePort.deactivateAllByOfferer(userId);
                serviceRequestCommandServicePort.cancelActiveRequestsForRole(userId, true);
            }
            case CLIENT -> serviceRequestCommandServicePort.cancelActiveRequestsForRole(userId, false);
            case ADMIN -> {
                // Sin datos operativos asociados.
            }
        }

        userRoleServicePort.revokeRole(userId, role);

        notificationServicePort.notify(
                userId,
                "role_revoked",
                "Se retiro un rol de tu cuenta",
                "Un administrador retiro el rol " + role.name() + " de tu cuenta.",
                "USER",
                userId,
                null,
                Map.of());
    }

    /**
     * RF-068: edicion de un usuario por el administrador. Solo se tocan los campos enviados (PATCH
     * parcial). Los datos PII (telefono) se cifran al persistir igual que en el resto del sistema, y el
     * documento sigue siendo inmutable (no viaja en el command), como en RF-006.
     */
    @Override
    @Transactional
    public void updateUserByAdmin(Long adminId, Long userId, UpdateUserByAdminCommand command) {
        userQueryServicePort.getUserById(userId); // 404 si no existe

        if (command.email() != null) {
            userServicePort.changeEmail(new ChangeEmailCommand(userId, command.email()));
        }
        // Reutiliza el mismo caso de uso de RF-006: filtro de palabras, PATCH parcial y cifrado de PII
        // viven ahi, no se duplican aqui.
        userProfileServicePort.patchProfile(new UpdateProfileCommand(
                userId, command.fullName(), command.phone(), command.photoUrl(), command.description()));
    }

    private RoleName parseRole(String roleName) {
        try {
            return RoleName.valueOf(roleName == null ? "" : roleName.strip().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new InvalidStateException("Invalid role: " + roleName);
        }
    }

    @Override
    public Page<UserSummaryItem> searchUsers(SearchUsersQuery query, Pageable pageable) {
        return userQueryServicePort.searchUsers(query, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public UserAdminDetailResult getUserAdminDetail(Long userId) {
        User user = userQueryServicePort.getUserById(userId);
        List<String> roles = userRoleServicePort.getUserRoles(userId).stream()
                .map(role -> role.getName().name())
                .toList();
        UserProfile profile = userProfileServicePort.getProfileInfo(userId);
        // Metricas de comportamiento leidas del modulo de metricas (fuente unica). getMainMetrics devuelve
        // ceros si el usuario no tiene ese rol, asi que ambos bloques vienen siempre presentes.
        OffererMetrics offererMetrics = offererMetricsServicePort.getMainMetrics(userId);
        ClientMetrics clientMetrics = clientMetricsServicePort.getMainMetrics(userId);
        // Reportes: moderacion, no metricas; conteo en el modulo reports.
        int reportsReceived = reportServicePort.countReportsByReportedUser(userId);
        int reportsSent = reportServicePort.countReportsByReporter(userId);
        return new UserAdminDetailResult(
                user.getId(), user.getEmail(), user.getBanned(), user.getDeletedAt(), user.getCreatedAt(), roles,
                profile.getFullName(), profile.getProfilePhotoUrl(), profile.getDocumentType(),
                profile.getDocumentNumber(), profile.getPhoneNumber(), profile.getBio(),
                profile.getProfileType() == null ? null : profile.getProfileType().name(),
                offererMetrics, clientMetrics, reportsReceived, reportsSent);
    }

    @Override
    @Transactional(readOnly = true)
    public AdminRequestDetailResult getRequestDetailForAdmin(Long requestId) {
        // Vista administrativa (ambas partes). El gate de rol ADMIN vive en el endpoint (Spring Security).
        return serviceRequestQueryServicePort.getRequestDetailForAdmin(requestId);
    }

    @Override
    public void banUser(Long adminId, Long userId, String reason) {
        adminActionGuard.requireNotSelf(adminId, userId);
        adminActionGuard.requireAnotherActiveAdmin(userId);
        userServicePort.banUser(userId, reason);
    }

    @Override
    public void unbanUser(Long adminId, Long userId) {
        userServicePort.unbanUser(userId);
    }

    @Override
    public void deleteUser(Long adminId, Long userId) {
        adminActionGuard.requireNotSelf(adminId, userId);
        adminActionGuard.requireAnotherActiveAdmin(userId);
        userDeletionServicePort.deleteUser(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AdminFeedbackSearchResult> searchFeedback(AdminFeedbackSearchQuery query, Pageable pageable) {
        Page<AdminFeedbackSearchResult> servicePage = searchServiceFeedback(query, pageable);
        Page<AdminFeedbackSearchResult> clientPage = searchClientFeedback(query, pageable);

        List<AdminFeedbackSearchResult> combined = Stream.concat(servicePage.getContent().stream(), clientPage.getContent().stream())
                .sorted(Comparator.comparing(AdminFeedbackSearchResult::createdAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), combined.size());
        List<AdminFeedbackSearchResult> pageContent = start >= combined.size() ? List.of() : combined.subList(start, end);
        long totalElements = servicePage.getTotalElements() + clientPage.getTotalElements();
        return new PageImpl<>(pageContent, pageable, totalElements);
    }

    private Page<AdminFeedbackSearchResult> searchServiceFeedback(AdminFeedbackSearchQuery query, Pageable pageable) {
        if (query.serviceId() == null && query.clientId() == null) {
            return Page.empty(pageable);
        }
        Page<ServiceFeedback> feedbackPage;
        if (query.serviceId() != null) {
            feedbackPage = serviceFeedbackPersistencePort.findByServiceIdAndFilters(
                    query.serviceId(), query.keyword(), query.ratingMin(), query.ratingMax(), pageable);
        } else {
            feedbackPage = serviceFeedbackPersistencePort.findByClientIdAndFilters(
                    query.clientId(), query.keyword(), query.ratingMin(), query.ratingMax(), pageable);
        }
        return feedbackPage.map(f -> new AdminFeedbackSearchResult(
                "SERVICE", f.getId(), f.getRequestId(),
                f.getClientId(), f.getServiceId(),
                f.getRating(), f.getComment(), f.getCreatedAt()));
    }

    private Page<AdminFeedbackSearchResult> searchClientFeedback(AdminFeedbackSearchQuery query, Pageable pageable) {
        if (query.clientId() == null && query.offererId() == null) {
            return Page.empty(pageable);
        }
        Page<ClientFeedback> feedbackPage;
        if (query.offererId() != null) {
            feedbackPage = clientFeedbackPersistencePort.findByOffererIdAndFilters(
                    query.offererId(), query.keyword(), query.ratingMin(), query.ratingMax(), pageable);
        } else {
            feedbackPage = clientFeedbackPersistencePort.findByClientIdAndFilters(
                    query.clientId(), query.keyword(), query.ratingMin(), query.ratingMax(), pageable);
        }
        return feedbackPage.map(f -> new AdminFeedbackSearchResult(
                "CLIENT", f.getId(), f.getRequestId(),
                f.getOffererId(), f.getClientId(),
                f.getRating(), f.getComment(), f.getCreatedAt()));
    }

    @Override
    public void deleteService(Long serviceId, Long adminId, boolean isAdmin) {
        marketplaceServicePort.delete(serviceId, adminId, isAdmin);
    }
}
