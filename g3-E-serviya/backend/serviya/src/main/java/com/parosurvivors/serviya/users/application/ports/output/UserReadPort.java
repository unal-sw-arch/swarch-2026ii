package com.parosurvivors.serviya.users.application.ports.output;

import com.parosurvivors.serviya.users.application.dto.item.UserSummaryItem;
import com.parosurvivors.serviya.users.application.dto.query.SearchUsersQuery;
import com.parosurvivors.serviya.users.domain.User;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Puerto de salida de LECTURA (CQRS) de usuarios: los finds de dominio y la busqueda enriquecida paginada
 * del panel admin. La busqueda usa query nativa (join a user_profiles para nombre/foto) y
 * {@code @SqlResultSetMapping}; el filtro por rol llega ya resuelto a {@code roleId} (el nombre se traduce
 * a id en el servicio, con una consulta unica sobre roles) para no unir la tabla roles. El puerto de
 * escritura ({@link UserPersistencePort}) queda solo con save/update/delete.
 */
public interface UserReadPort {

    Optional<User> findById(Long id);

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    Page<UserSummaryItem> searchUsers(SearchUsersQuery query, Integer roleId, Pageable pageable);
}
