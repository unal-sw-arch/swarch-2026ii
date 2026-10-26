package com.parosurvivors.serviya.services.infrastructure.adapters.input.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import org.springframework.http.ResponseEntity;

import com.parosurvivors.serviya.services.infrastructure.dto.response.CategoryResponse;
import com.parosurvivors.serviya.services.infrastructure.dto.form.CreateCategoryForm;


import java.util.List;


@Tag(name = "Categories", description = "API de gestión de categorías del marketplace")
public interface CategoryApi {
    
    @Operation(summary = "Crear una nueva categoría")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Categoría creada exitosamente"),
        @ApiResponse(responseCode = "400", description = "Datos inválidos")
    })
    public ResponseEntity<CategoryResponse> create(CreateCategoryForm form);
    
    @Operation(summary = "Obtener una categoría por su ID")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Categoría encontrada"),
        @ApiResponse(responseCode = "404", description = "Categoría no encontrada")
    })
    public ResponseEntity<CategoryResponse> getById(Long id);
    
    @Operation(summary = "Obtener todas las categorías")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista de categorías"),
    })
    public ResponseEntity<List<CategoryResponse>> getAll();

}
