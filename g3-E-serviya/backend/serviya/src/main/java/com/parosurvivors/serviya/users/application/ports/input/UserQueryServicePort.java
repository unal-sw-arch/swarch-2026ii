package com.parosurvivors.serviya.users.application.ports.input;

import com.parosurvivors.serviya.users.application.dto.item.UserSummaryItem;
import com.parosurvivors.serviya.users.application.dto.query.SearchUsersQuery;
import com.parosurvivors.serviya.users.domain.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Puerto de entrada de lectura de usuarios (CQRS). Lo consume el modulo admin (panel de administracion)
 * a traves de este puerto de entrada, nunca del repositorio. Devuelve read-models (Item) o el dominio User;
 * nunca tipos web. Ver documents/project-structure/estructura-servicios.docx (modulo 9, searchUsers).
 */
public interface UserQueryServicePort {

    Page<UserSummaryItem> searchUsers(SearchUsersQuery query, Pageable pageable);

    /** Carga el usuario por id (lanza ResourceNotFoundException si no existe). */
    User getUserById(Long id);
}
