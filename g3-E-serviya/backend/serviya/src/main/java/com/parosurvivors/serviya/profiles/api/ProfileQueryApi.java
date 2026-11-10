package com.parosurvivors.serviya.profiles.api;

import com.parosurvivors.serviya.profiles.api.dto.AddressSnapshot;
import com.parosurvivors.serviya.profiles.api.dto.OffererAvailabilitySnapshot;
import com.parosurvivors.serviya.profiles.api.dto.OffererProfileSnapshot;
import com.parosurvivors.serviya.profiles.api.dto.OffererProfileSummarySnapshot;
import com.parosurvivors.serviya.profiles.api.dto.ProfileSnapshot;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Consultas publicas del modulo profiles.
 */
public interface ProfileQueryApi {

    Optional<ProfileSnapshot> findProfile(Long userId);

    /** Lectura por lote para evitar N+1 en listados compuestos (admin/BFF). */
    List<ProfileSnapshot> findProfiles(Collection<Long> userIds);

    Optional<OffererProfileSnapshot> findOffererProfile(Long userId);

    /**
     * Resumen publico del oferente. No incluye rating: ese dato lo aporta {@code metrics.api}.
     * Requiere que existan tanto {@code user_profiles} como {@code offerer_profiles}.
     */
    Optional<OffererProfileSummarySnapshot> getOffererSummary(Long userId);

    Optional<AddressSnapshot> findAddress(Long addressId);

    List<AddressSnapshot> getAddresses(Long userId);

    /** Franjas de disponibilidad del oferente (weekDay 0 = lunes ... 6 = domingo). */
    List<OffererAvailabilitySnapshot> getAvailabilities(Long offererId);

    /**
     * Ids de usuarios cuyo nombre completo contiene el fragmento (sin distinguir mayusculas).
     * Permite que consumidores como services filtren por nombre de oferente sin tocar
     * {@code user_profiles}.
     */
    List<Long> findUserIdsByFullName(String nameFragment);

    /**
     * Ids de usuarios cuya direccion principal esta dentro del radio (Haversine, km).
     * La geolocalizacion es un dato propio de profiles.
     */
    List<Long> findUserIdsWithinDistance(double latitude, double longitude, double maxDistanceKm);
}
