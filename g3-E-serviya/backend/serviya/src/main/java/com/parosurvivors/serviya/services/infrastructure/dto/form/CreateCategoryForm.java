package com.parosurvivors.serviya.services.infrastructure.dto.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Datos para crear una categoría")
public record CreateCategoryForm(
    @NotBlank @Size(max = 150) String name
){}
