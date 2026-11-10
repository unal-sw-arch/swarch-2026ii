package com.parosurvivors.serviya.shared.contract;

import java.util.List;

/**
 * Resultado paginado neutro para las APIs publicas de los modulos.
 *
 * <p>No expone tipos de Spring ({@code Page}, {@code Pageable}) para que los contratos puedan
 * reutilizarse tal cual cuando un modulo se extraiga a un servicio HTTP.</p>
 */
public record PageResult<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public PageResult {
        content = content == null ? List.of() : List.copyOf(content);
    }
}
