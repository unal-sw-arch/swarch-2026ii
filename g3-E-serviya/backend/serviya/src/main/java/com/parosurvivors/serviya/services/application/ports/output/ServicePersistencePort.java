package com.parosurvivors.serviya.services.application.ports.output;

import com.parosurvivors.serviya.services.application.dto.query.SearchServiceQuery;
import com.parosurvivors.serviya.services.domain.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface ServicePersistencePort {
    Service save(Service service);
    Optional<Service> findById(Long id);
    List<Service> findAll();
    List<Service> findByOffererId(Long offererId);
    Page<Service> search(SearchServiceQuery criteria, Pageable pageable);
    void deleteById(Long id);
    Service update(Service service);
}
