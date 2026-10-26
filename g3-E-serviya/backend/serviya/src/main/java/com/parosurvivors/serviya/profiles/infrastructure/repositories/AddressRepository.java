package com.parosurvivors.serviya.profiles.infrastructure.repositories;

import com.parosurvivors.serviya.profiles.infrastructure.entities.AddressEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AddressRepository extends JpaRepository<AddressEntity, Long> {
    List<AddressEntity> findByUserId(Long userId);
}
