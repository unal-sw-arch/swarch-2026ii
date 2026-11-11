package com.parosurvivors.serviya.services.api;

import com.parosurvivors.serviya.services.api.dto.CategorySnapshot;
import com.parosurvivors.serviya.services.api.dto.ServiceAvailabilitySnapshot;
import com.parosurvivors.serviya.services.api.dto.ServiceSearchCriteria;
import com.parosurvivors.serviya.services.api.dto.ServiceSnapshot;
import com.parosurvivors.serviya.shared.contract.PageResult;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Consultas publicas del modulo services.
 */
public interface ServiceQueryApi {

    Optional<ServiceSnapshot> findById(Long serviceId);

    /** Lectura por lote para evitar N+1 en listados compuestos (requests/BFF). */
    List<ServiceSnapshot> findByIds(Collection<Long> serviceIds);

    /** Servicios del oferente, incluidos inactivos o soft-deleted; el consumidor filtra. */
    List<ServiceSnapshot> findByOffererId(Long offererId);

    Optional<CategorySnapshot> findCategory(Long categoryId);

    List<CategorySnapshot> getCategories();

    /** Franjas del servicio (weekDay 1 = lunes ... 7 = domingo). */
    List<ServiceAvailabilitySnapshot> getAvailabilities(Long serviceId);

    /**
     * Busqueda paginada solo con datos propios. El consumidor/BFF resuelve antes los ids de
     * rating/nombre/geolocalizacion con metrics.api y profiles.api.
     */
    PageResult<ServiceSnapshot> search(ServiceSearchCriteria criteria, int page, int size);
}
