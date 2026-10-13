package com.parosurvivors.serviya.services.application.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.parosurvivors.serviya.services.application.dto.command.CreateCategoryCommand;
import com.parosurvivors.serviya.services.application.mappers.CategoryCommandMapper;
import com.parosurvivors.serviya.services.application.ports.output.CategoryPersistencePort;
import com.parosurvivors.serviya.services.domain.Category;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class MarketplaceCategoryTest {

    @Mock private CategoryPersistencePort persistencePort;
    @Mock private CategoryCommandMapper mapper;

    @InjectMocks private MarketplaceCategory service;

    private static final Long CATEGORY_ID = 1L;

    private Category sampleCategory() {
        return Category.builder().id(CATEGORY_ID).name("Hogar").build();
    }

    @Test
    void createMapsCommandAndSaves() {
        CreateCategoryCommand command = new CreateCategoryCommand("Plomeria");
        Category mapped = Category.builder().name("Plomeria").build();

        when(mapper.toDomain(command)).thenReturn(mapped);
        when(persistencePort.save(any())).thenAnswer(inv -> {
            Category c = inv.getArgument(0);
            c.setId(CATEGORY_ID);
            return c;
        });

        Category result = service.create(command);

        ArgumentCaptor<Category> captor = ArgumentCaptor.forClass(Category.class);
        verify(mapper).toDomain(command);
        verify(persistencePort).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("Plomeria");
        assertThat(result.getId()).isEqualTo(CATEGORY_ID);
    }

    @Test
    void getByIdReturnsCategory() {
        when(persistencePort.findById(CATEGORY_ID)).thenReturn(Optional.of(sampleCategory()));

        Optional<Category> result = service.getById(CATEGORY_ID);

        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("Hogar");
    }

    @Test
    void getByIdReturnsEmptyWhenNotFound() {
        when(persistencePort.findById(99L)).thenReturn(Optional.empty());

        Optional<Category> result = service.getById(99L);

        assertThat(result).isEmpty();
    }

    @Test
    void getAllReturnsAllCategories() {
        Category cat1 = Category.builder().id(1L).name("Hogar").build();
        Category cat2 = Category.builder().id(2L).name("Plomeria").build();
        when(persistencePort.findAll()).thenReturn(List.of(cat1, cat2));

        List<Category> result = service.getAll();

        assertThat(result).hasSize(2);
        assertThat(result).extracting(Category::getName).containsExactly("Hogar", "Plomeria");
    }
}
