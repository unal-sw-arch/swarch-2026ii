package com.parosurvivors.serviya.profiles.infrastructure.repositories;

import com.parosurvivors.serviya.profiles.infrastructure.entities.AddressEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AddressRepository extends JpaRepository<AddressEntity, Long> {
    List<AddressEntity> findByUserId(Long userId);

    /**
     * Usuarios cuya direccion principal cae dentro del radio (Haversine, km).
     * Las funciones trigonometricas las soporta Hibernate 6 en HQL.
     */
    @Query("""
            SELECT p.userId
            FROM UserProfileEntity p, AddressEntity a
            WHERE p.primaryAddressId = a.id
              AND (6371.0 * acos(
                    cos(radians(:latitude)) * cos(radians(a.latitude))
                        * cos(radians(a.longitude) - radians(:longitude))
                    + sin(radians(:latitude)) * sin(radians(a.latitude))
                  )) <= :maxDistanceKm
            """)
    List<Long> findUserIdsWithinDistance(
            @Param("latitude") double latitude,
            @Param("longitude") double longitude,
            @Param("maxDistanceKm") double maxDistanceKm);
}
