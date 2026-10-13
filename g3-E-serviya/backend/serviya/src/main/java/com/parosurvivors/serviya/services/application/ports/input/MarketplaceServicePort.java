package com.parosurvivors.serviya.services.application.ports.input;

import com.parosurvivors.serviya.metrics.domain.ServiceMetrics;
import com.parosurvivors.serviya.services.application.dto.command.CreateServiceCommand;
import com.parosurvivors.serviya.services.application.dto.command.UpdateServiceCommand;
import com.parosurvivors.serviya.services.application.dto.query.SearchServiceQuery;
import com.parosurvivors.serviya.services.domain.Service;
import com.parosurvivors.serviya.services.domain.ServiceDetail;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Puerto de entrada del marketplace de servicios. Recibe Commands y devuelve la entidad de dominio
 * (Service); nunca tipos web (Form/Response). El mapeo web<->aplicacion vive en ServiceWebMapper.
 */
public interface MarketplaceServicePort {
    

    Service create(CreateServiceCommand command);
    Optional<Service> getById(Long id);
    List<Service> getAll();
    List<ServiceDetail> getByOffererId(Long offererId);
    Optional<ServiceDetail> getDetailById(Long id);
    Service update(UpdateServiceCommand command, Long requesterId, boolean isAdmin);
    Page<Service> search(SearchServiceQuery criteria, Pageable pageable);
    Map<Long, ServiceMetrics> getMetricsForServices(List<Long> serviceIds);
    void delete(Long id, Long requesterId, boolean isAdmin);
    void softDelete(Long id, Long requesterId, boolean isAdmin);
    void activate(Long id, Long requesterId, boolean isAdmin);
    void deactivate(Long id, Long requesterId, boolean isAdmin);
    void deactivateAllByOfferer(Long offererId);
    
}
