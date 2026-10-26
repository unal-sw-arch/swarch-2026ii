package com.parosurvivors.serviya.services.application.mappers;

import com.parosurvivors.serviya.services.application.dto.command.CreateCategoryCommand;
import com.parosurvivors.serviya.services.domain.Category;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CategoryCommandMapper {

    @Mapping(target = "id", ignore = true)
    Category toDomain(CreateCategoryCommand command);

}
