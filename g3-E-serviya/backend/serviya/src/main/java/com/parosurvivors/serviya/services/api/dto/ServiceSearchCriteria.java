package com.parosurvivors.serviya.services.api.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Criterios de busqueda de servicios. Los ids resueltos por otros modulos (rating, nombre de
 * oferente y geolocalizacion) llegan ya calculados por el BFF; services solo consulta sus datos.
 */
public record ServiceSearchCriteria(
        String name,
        Long categoryId,
        Long offererId,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        Boolean available,
        Double minRating,
        Double maxRating,
        Double latitude,
        Double longitude,
        Double maxDistanceKm,
        List<Long> serviceIds,
        List<Long> offererIdsByName,
        List<Long> offererIdsWithinDistance) {

    public ServiceSearchCriteria withServiceIds(List<Long> ids) {
        return new ServiceSearchCriteria(
                name, categoryId, offererId, minPrice, maxPrice, available,
                minRating, maxRating, latitude, longitude, maxDistanceKm,
                ids, offererIdsByName, offererIdsWithinDistance);
    }

    public ServiceSearchCriteria withOffererIdsByName(List<Long> ids) {
        return new ServiceSearchCriteria(
                name, categoryId, offererId, minPrice, maxPrice, available,
                minRating, maxRating, latitude, longitude, maxDistanceKm,
                serviceIds, ids, offererIdsWithinDistance);
    }

    public ServiceSearchCriteria withOffererIdsWithinDistance(List<Long> ids) {
        return new ServiceSearchCriteria(
                name, categoryId, offererId, minPrice, maxPrice, available,
                minRating, maxRating, latitude, longitude, maxDistanceKm,
                serviceIds, offererIdsByName, ids);
    }
}
