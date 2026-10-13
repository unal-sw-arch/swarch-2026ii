package com.parosurvivors.serviya.services.infrastructure.adapters.output;

import java.util.List;
import java.util.Optional;

import com.parosurvivors.serviya.services.application.ports.output.CategoryPersistencePort;
import com.parosurvivors.serviya.services.domain.Category;
import com.parosurvivors.serviya.services.infrastructure.repositories.CategoryRepository;
import com.parosurvivors.serviya.services.infrastructure.entities.CategoryEntity;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class CategoryPersistenceAdapter implements CategoryPersistencePort {

    private final CategoryRepository repository;

    @Override
    public Optional<Category> findById(Long id) {
        return repository.findById(id)
                .map(this::toDomainModel);
    }

    @Override
    public List<Category> findAll() {
        return repository.findAll().stream()
                .map(this::toDomainModel)
                .collect(Collectors.toList());
    }
    
    @Override
    public Category save(Category category) {
    
        CategoryEntity entity = toDomainEntity(category);
        CategoryEntity saved = repository.save(entity);
        return toDomainModel(saved);
    }


    private CategoryEntity toDomainEntity(Category domain) {
        CategoryEntity entity = new CategoryEntity();
        entity.setId(domain.getId());
        entity.setName(domain.getName());
        return entity;
    }

    private Category toDomainModel(CategoryEntity entity) {
        return new Category(
                entity.getId(),
                entity.getName()
        );
    }

    
}
