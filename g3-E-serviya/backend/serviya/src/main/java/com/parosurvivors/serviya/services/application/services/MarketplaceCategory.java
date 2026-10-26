package com.parosurvivors.serviya.services.application.services;


import com.parosurvivors.serviya.services.application.dto.command.CreateCategoryCommand;
import com.parosurvivors.serviya.services.application.mappers.CategoryCommandMapper;
import com.parosurvivors.serviya.services.application.ports.input.MarketplaceCategoryPort;
import com.parosurvivors.serviya.services.application.ports.output.CategoryPersistencePort;
import com.parosurvivors.serviya.services.domain.Category;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class MarketplaceCategory implements MarketplaceCategoryPort {
    
    private final CategoryPersistencePort persistencePort;
    private final CategoryCommandMapper mapper;
    
    @Override
    public Category create(CreateCategoryCommand command) {
        Category category = mapper.toDomain(command);
        
        return persistencePort.save(category);
    }

    @Override
    public Optional<Category> getById(Long id) {
        return persistencePort.findById(id);
    }

    @Override
    public List<Category> getAll() {
        return persistencePort.findAll().stream()
                .collect(Collectors.toList());
    }
    
    
}
