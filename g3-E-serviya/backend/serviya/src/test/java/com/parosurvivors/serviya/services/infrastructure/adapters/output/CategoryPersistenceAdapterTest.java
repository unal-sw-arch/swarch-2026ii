package com.parosurvivors.serviya.services.infrastructure.adapters.output;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.parosurvivors.serviya.services.domain.Category;
import com.parosurvivors.serviya.services.infrastructure.entities.CategoryEntity;
import com.parosurvivors.serviya.services.infrastructure.repositories.CategoryRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class CategoryPersistenceAdapterTest {

    @Mock private CategoryRepository repository;

    @InjectMocks private CategoryPersistenceAdapter adapter;

    private static final Long CATEGORY_ID = 1L;

    private CategoryEntity entityCategory() {
        CategoryEntity e = new CategoryEntity();
        e.setId(CATEGORY_ID);
        e.setName("Hogar");
        return e;
    }

    private Category domainCategory() {
        return Category.builder().id(CATEGORY_ID).name("Hogar").build();
    }

    @Test
    void findByIdReturnsMappedCategory() {
        when(repository.findById(CATEGORY_ID)).thenReturn(Optional.of(entityCategory()));

        Optional<Category> result = adapter.findById(CATEGORY_ID);

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(CATEGORY_ID);
        assertThat(result.get().getName()).isEqualTo("Hogar");
    }

    @Test
    void findByIdReturnsEmptyWhenNotFound() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        Optional<Category> result = adapter.findById(99L);

        assertThat(result).isEmpty();
    }

    @Test
    void findAllReturnsAllCategories() {
        CategoryEntity ent1 = entityCategory();
        CategoryEntity ent2 = new CategoryEntity();
        ent2.setId(2L);
        ent2.setName("Plomeria");

        when(repository.findAll()).thenReturn(List.of(ent1, ent2));

        List<Category> result = adapter.findAll();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getName()).isEqualTo("Hogar");
        assertThat(result.get(1).getName()).isEqualTo("Plomeria");
    }

    @Test
    void saveConvertsAndPersists() {
        Category domain = domainCategory();
        CategoryEntity saved = entityCategory();
        when(repository.save(any(CategoryEntity.class))).thenReturn(saved);

        Category result = adapter.save(domain);

        ArgumentCaptor<CategoryEntity> captor = ArgumentCaptor.forClass(CategoryEntity.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("Hogar");
        assertThat(result.getId()).isEqualTo(CATEGORY_ID);
        assertThat(result.getName()).isEqualTo("Hogar");
    }
}
