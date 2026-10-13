package com.parosurvivors.serviya.shared.infrastructure.repositories;

import org.springframework.stereotype.Repository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.parosurvivors.serviya.shared.dto.response.PlatformStatsResponse;
import com.parosurvivors.serviya.users.infrastructure.repositories.UserRepository;
import com.parosurvivors.serviya.services.infrastructure.repositories.ServiceRepository;
import com.parosurvivors.serviya.requests.infrastructure.repositories.ServiceRequestRepository;
import com.parosurvivors.serviya.requests.domain.RequestStatus;
import com.parosurvivors.serviya.feedback.infrastructure.repositories.ServiceFeedbackRepository;
import com.parosurvivors.serviya.services.infrastructure.repositories.CategoryRepository;

/**
 * Repositorio que calcula las estadísticas globales de la plataforma.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class PlatformStatsRepository {
    
    private final UserRepository userRepository;
    private final ServiceRepository serviceRepository;
    private final ServiceRequestRepository serviceRequestRepository;
    private final ServiceFeedbackRepository serviceFeedbackRepository;
    private final CategoryRepository categoryRepository;
    
    public PlatformStatsResponse getPlatformStats() {
        try {
            // Contar oferentes activos (usuarios con rol OFFERER)
            // Query nativa para contar usuarios distintos que tienen el rol OFFERER
            long activeOfferers = userRepository.countByRolesName("OFFERER");
            log.info("Active offerers count: {}", activeOfferers);
            
            // Contar servicios completados (requests en estado COMPLETED)
            long completedServices = serviceRequestRepository.countByStatus(RequestStatus.COMPLETED);
            log.info("Completed services count: {}", completedServices);
            
            // Calificación promedio (de todas las reseñas)
            Double averageRating = serviceFeedbackRepository.findAverageRating();
            if (averageRating == null || averageRating.isNaN()) {
                averageRating = 0.0;
            }
            log.info("Average rating: {}", averageRating);
            
            // Total de categorías
            long totalCategories = categoryRepository.count();
            log.info("Total categories count: {}", totalCategories);
            
            return new PlatformStatsResponse(
                activeOfferers,
                completedServices,
                Math.min(5.0, Math.max(0.0, averageRating)), // Clamped to [0, 5]
                totalCategories
            );
        } catch (Exception e) {
            log.error("Error retrieving platform stats", e);
            // Retorna valores por defecto si hay error
            return new PlatformStatsResponse(0L, 0L, 0.0, 0L);
        }
    }
}
