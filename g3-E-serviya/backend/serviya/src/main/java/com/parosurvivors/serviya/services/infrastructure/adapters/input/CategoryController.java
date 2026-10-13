package com.parosurvivors.serviya.services.infrastructure.adapters.input;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.parosurvivors.serviya.services.application.ports.input.MarketplaceCategoryPort;
import com.parosurvivors.serviya.services.infrastructure.mappers.CategoryWebMapper;
import com.parosurvivors.serviya.services.infrastructure.dto.response.CategoryResponse;
import com.parosurvivors.serviya.services.infrastructure.dto.form.CreateCategoryForm;
import com.parosurvivors.serviya.services.infrastructure.adapters.input.api.CategoryApi;

import java.util.List;


@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
@Tag(name = "Categories", description = "API de gestión de categorías del marketplace")
public class CategoryController implements CategoryApi {
    
    private final MarketplaceCategoryPort marketplaceCategory;
    private final CategoryWebMapper mapper;

    @Override
    @PostMapping
    public ResponseEntity<CategoryResponse> create(@Valid @RequestBody CreateCategoryForm form) {
        CategoryResponse response = mapper.toResponse(
            marketplaceCategory.create(mapper.toCommand(form))
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Override
    @GetMapping("/{id}")
    public ResponseEntity<CategoryResponse> getById(
        @Parameter(description = "ID de la categoría") @PathVariable Long id) {
        return marketplaceCategory.getById(id)
            .map(mapper::toResponse)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @Override
    @GetMapping
    public ResponseEntity<List<CategoryResponse>> getAll() {
        List<CategoryResponse> categories = mapper.toResponses(marketplaceCategory.getAll());
        return ResponseEntity.ok(categories);
    }
}
