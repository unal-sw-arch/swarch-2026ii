package com.parosurvivors.serviya.statistics.application.services;

import com.parosurvivors.serviya.feedback.api.FeedbackQueryApi;
import com.parosurvivors.serviya.requests.api.RequestQueryApi;
import com.parosurvivors.serviya.services.api.ServiceQueryApi;
import com.parosurvivors.serviya.shared.contract.Roles;
import com.parosurvivors.serviya.statistics.application.dto.response.PlatformStatsResponse;
import com.parosurvivors.serviya.users.api.UserQueryApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Read model de estadisticas de la plataforma. Compone datos publicos de users/requests/feedback/
 * services sin tocar sus repositorios, tablas ni dominio.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlatformStatsService {

    private final UserQueryApi userQueryApi;
    private final RequestQueryApi requestQueryApi;
    private final FeedbackQueryApi feedbackQueryApi;
    private final ServiceQueryApi serviceQueryApi;

    public PlatformStatsResponse getPlatformStats() {
        try {
            long activeOfferers = userQueryApi.findUserIdsByRole(Roles.OFFERER).size();
            long completedServices = requestQueryApi.countByStatus("COMPLETED");
            double averageRating = clamp(feedbackQueryApi.averageServiceFeedbackRating());
            long totalCategories = serviceQueryApi.getCategories().size();
            return new PlatformStatsResponse(activeOfferers, completedServices, averageRating, totalCategories);
        } catch (Exception ex) {
            log.error("Error retrieving platform stats", ex);
            return new PlatformStatsResponse(0L, 0L, 0.0, 0L);
        }
    }

    private double clamp(double rating) {
        return Math.min(5.0, Math.max(0.0, rating));
    }
}
