package com.parosurvivors.serviya.services.infrastructure.mappers;

import com.parosurvivors.serviya.services.domain.Category;
import com.parosurvivors.serviya.services.infrastructure.dto.form.CreateCategoryForm;
import com.parosurvivors.serviya.services.infrastructure.dto.response.CategoryResponse;
import com.parosurvivors.serviya.services.application.dto.command.CreateCategoryCommand;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface CategoryWebMapper {
    
    @Mapping(target = "name", source = "name")
    CreateCategoryCommand toCommand(CreateCategoryForm form);

    CategoryResponse toResponse(Category category);

    List<CategoryResponse> toResponses(List<Category> categories);
}
