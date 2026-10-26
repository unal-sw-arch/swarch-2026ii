package com.parosurvivors.serviya.services.infrastructure.repositories;

import com.parosurvivors.serviya.services.infrastructure.entities.CategoryEntity; 
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CategoryRepository extends JpaRepository<CategoryEntity, Long> {
    
}
