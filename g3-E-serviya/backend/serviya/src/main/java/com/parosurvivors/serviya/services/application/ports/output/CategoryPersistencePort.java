package com.parosurvivors.serviya.services.application.ports.output;

import com.parosurvivors.serviya.services.domain.Category;

import java.util.List;
import java.util.Optional;

public interface CategoryPersistencePort {
    Optional<Category> findById(Long id);
    List<Category> findAll();
    Category save(Category category);

    // Pendent, add methods for updating, and deleting categories if needed
}
