package com.parosurvivors.serviya.services.infrastructure.repositories;

import com.parosurvivors.serviya.services.infrastructure.entities.ServiceAvailabilityEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ServiceAvailabilityRepository extends JpaRepository<ServiceAvailabilityEntity, Long> {
    
    List<ServiceAvailabilityEntity> findByServiceId(Long serviceId);
    
}
