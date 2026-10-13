package com.parosurvivors.serviya.services.infrastructure.repositories;

import com.parosurvivors.serviya.metrics.infrastructure.entities.ServiceMetricsEntity;
import com.parosurvivors.serviya.profiles.infrastructure.entities.AddressEntity;
import com.parosurvivors.serviya.profiles.infrastructure.entities.UserProfileEntity;
import com.parosurvivors.serviya.services.application.dto.query.SearchServiceQuery;
import com.parosurvivors.serviya.services.infrastructure.entities.ServiceEntity;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Especificacion JPA para busqueda dinamica de servicios.
 * Todos los filtros son opcionales (null = ignorado).
 * Siempre excluye registros con soft-delete (deletedAt IS NOT NULL).
 *
 * JOINs utilizados:
 *   - service_metrics  (subquery por serviceId)          → filtros minRating / maxRating
 *   - user_profiles    (subquery por offererId)           → filtro name (fullName)
 *   - user_profiles    (CROSS JOIN por offererId)         → geolocalización
 *   - addresses        (CROSS JOIN por primaryAddressId)  → geolocalización
 */
public class ServiceSpecificationRepository {

    private ServiceSpecificationRepository() {}

    public static Specification<ServiceEntity> fromQuery(SearchServiceQuery q) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Evitar filas duplicadas cuando hay JOINs con colecciones
            if (query != null) {
                query.distinct(true);
            }

            // ── Siempre excluir soft-deleted ─────────────────────────────────
            predicates.add(cb.isNull(root.get("deletedAt")));

            // ── Filtros sobre services ────────────────────────────────────────
            if (q.name() != null && !q.name().isBlank()) {
                String pattern = "%" + q.name().toLowerCase() + "%";

                // Subquery para buscar oferentes cuyo fullName coincida,
                // evitando un JOIN directo sobre columna no-PK (offerer_id → user_id)
                // que Hibernate no puede hacer lazy y provocaría carga del PiiAttributeConverter.
                Subquery<Long> offererSubquery = query.subquery(Long.class);
                Root<UserProfileEntity> profileRoot = offererSubquery.from(UserProfileEntity.class);
                offererSubquery.select(profileRoot.get("userId"))
                        .where(cb.like(cb.lower(profileRoot.get("fullName")), pattern));

                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("title")), pattern),
                        cb.like(cb.lower(root.get("description")), pattern),
                        root.get("offererId").in(offererSubquery)
                ));
            }

            if (q.categoryId() != null) {
                predicates.add(cb.equal(root.get("categoryId"), q.categoryId()));
            }

            if (q.offererId() != null) {
                predicates.add(cb.equal(root.get("offererId"), q.offererId()));
            }

            if (q.minPrice() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("priceHourly"), q.minPrice()));
            }

            if (q.maxPrice() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("priceHourly"), q.maxPrice()));
            }

            if (q.available() != null) {
                predicates.add(cb.equal(root.get("active"), q.available()));
            }

            // ── Rating via subquery (ServiceEntity no tiene mapeo JPA a ServiceMetricsEntity) ──
            if (q.minRating() != null || q.maxRating() != null) {
                Subquery<Long> ratingSub = query.subquery(Long.class);
                Root<ServiceMetricsEntity> mRoot = ratingSub.from(ServiceMetricsEntity.class);

                List<Predicate> ratingPredicates = new ArrayList<>();
                ratingPredicates.add(cb.equal(mRoot.get("serviceId"), root.get("id")));

                if (q.minRating() != null) {
                    ratingPredicates.add(cb.greaterThanOrEqualTo(
                            mRoot.get("averageRating"),
                            BigDecimal.valueOf(q.minRating())
                    ));
                }
                if (q.maxRating() != null) {
                    ratingPredicates.add(cb.lessThanOrEqualTo(
                            mRoot.get("averageRating"),
                            BigDecimal.valueOf(q.maxRating())
                    ));
                }

                ratingSub.select(mRoot.get("serviceId"))
                        .where(ratingPredicates.toArray(new Predicate[0]));
                predicates.add(root.get("id").in(ratingSub));
            }

            // ── Geolocalización (Haversine) ───────────────────────────────────
            // Se resuelve la cadena: service.offererId → user_profiles.userId
            //                         user_profiles.primaryAddressId → addresses.id
            if (q.latitude() != null && q.longitude() != null && q.maxDistanceKm() != null) {
                Root<UserProfileEntity> profile = query.from(UserProfileEntity.class);
                Root<AddressEntity> address = query.from(AddressEntity.class);

                predicates.add(cb.equal(profile.get("userId"), root.get("offererId")));
                predicates.add(cb.equal(address.get("id"), profile.get("primaryAddressId")));
                predicates.add(cb.isNotNull(profile.get("primaryAddressId")));

                Expression<Double> latRad  = cb.function("radians", Double.class, cb.literal(q.latitude()));
                Expression<Double> lngRad  = cb.function("radians", Double.class, cb.literal(q.longitude()));
                Expression<Double> aLatRad = cb.function("radians", Double.class, address.get("latitude"));
                Expression<Double> aLngRad = cb.function("radians", Double.class, address.get("longitude"));

                Expression<Double> cosLatU = cb.function("cos", Double.class, latRad);
                Expression<Double> sinLatU = cb.function("sin", Double.class, latRad);
                Expression<Double> cosLatA = cb.function("cos", Double.class, aLatRad);
                Expression<Double> sinLatA = cb.function("sin", Double.class, aLatRad);
                Expression<Double> cosLngD = cb.function("cos", Double.class, cb.diff(aLngRad, lngRad));

                Expression<Double> acosArg = cb.sum(
                        cb.prod(cosLatU, cb.prod(cosLatA, cosLngD)),
                        cb.prod(sinLatU, sinLatA)
                );
                Expression<Double> distance = cb.prod(
                        cb.literal(6371.0),
                        cb.function("acos", Double.class, acosArg)
                );

                predicates.add(cb.lessThanOrEqualTo(distance, q.maxDistanceKm()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

}
