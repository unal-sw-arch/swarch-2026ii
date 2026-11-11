package com.parosurvivors.serviya.services.infrastructure.repositories;

import com.parosurvivors.serviya.services.application.dto.query.SearchServiceQuery;
import com.parosurvivors.serviya.services.infrastructure.entities.ServiceEntity;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * Especificacion JPA para busqueda dinamica de servicios.
 * Todos los filtros son opcionales (null = ignorado).
 * Siempre excluye registros con soft-delete (deletedAt IS NOT NULL).
 *
 * <p>Los filtros que dependen de datos de otros modulos llegan resueltos como ids:
 * rating desde {@code metrics.api}, nombre del oferente y geolocalizacion desde
 * {@code profiles.api}. Esta clase no importa entidades ajenas.</p>
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
                Predicate nameMatch = cb.or(
                        cb.like(cb.lower(root.get("title")), pattern),
                        cb.like(cb.lower(root.get("description")), pattern));

                // Ids de oferentes cuyo fullName coincide, resueltos por profiles.api.
                if (q.offererIdsByName() != null && !q.offererIdsByName().isEmpty()) {
                    nameMatch = cb.or(nameMatch,
                            root.get("offererId").in(q.offererIdsByName()));
                }
                predicates.add(nameMatch);
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

            // ── Rating: los ids se resuelven en metrics.api y llegan ya filtrados ──
            if (q.serviceIds() != null && !q.serviceIds().isEmpty()) {
                predicates.add(root.get("id").in(q.serviceIds()));
            }

            // ── Geolocalización: los ids de oferentes llegan resueltos por profiles.api ──
            if (q.offererIdsWithinDistance() != null && !q.offererIdsWithinDistance().isEmpty()) {
                predicates.add(root.get("offererId").in(q.offererIdsWithinDistance()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

}
