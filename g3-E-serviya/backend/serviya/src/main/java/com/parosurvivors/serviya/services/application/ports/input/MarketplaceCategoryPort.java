package com.parosurvivors.serviya.services.application.ports.input;

import com.parosurvivors.serviya.services.application.dto.command.CreateCategoryCommand;
import com.parosurvivors.serviya.services.domain.Category;

import java.util.List;
import java.util.Optional;

public interface MarketplaceCategoryPort {

    Category create(CreateCategoryCommand command);
    Optional<Category> getById(Long id);
    List<Category> getAll();
    
}
