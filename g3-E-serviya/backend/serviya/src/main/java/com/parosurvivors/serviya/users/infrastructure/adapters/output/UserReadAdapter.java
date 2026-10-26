package com.parosurvivors.serviya.users.infrastructure.adapters.output;

import com.parosurvivors.serviya.users.application.dto.item.UserSummaryItem;
import com.parosurvivors.serviya.users.application.dto.query.SearchUsersQuery;
import com.parosurvivors.serviya.users.application.ports.output.UserReadPort;
import com.parosurvivors.serviya.users.domain.User;
import com.parosurvivors.serviya.users.infrastructure.mappers.UserPersistenceMapper;
import com.parosurvivors.serviya.users.infrastructure.repositories.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Component;

/**
 * Adapter de LECTURA (CQRS-light) de usuarios para el panel admin. La fila del listado cruza tablas
 * (users + user_profiles para nombre/foto), asi que se usa query nativa + {@code @SqlResultSetMapping}
 * ({@code UserSummaryMapping} definido en {@code UserEntity}), igual que la busqueda de solicitudes.
 *
 * <p>El filtro por nombre hace JOIN a {@code user_profiles} (columna no-PII). El filtro por rol llega ya
 * resuelto a {@code roleId} (el servicio traduce el nombre con una consulta unica sobre roles) y se aplica
 * con un EXISTS sobre {@code user_roles}, evitando unir la tabla roles. Los nombres de columna del ORDER BY
 * son literales controlados por este codigo (whitelist), no hay inyeccion.
 */
@Component
@RequiredArgsConstructor
public class UserReadAdapter implements UserReadPort {

    @PersistenceContext
    private EntityManager em;

    private final UserRepository repository;
    private final UserPersistenceMapper mapper;

    @Override
    public Optional<User> findById(Long id) {
        return repository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return repository.findByEmail(email).map(mapper::toDomain);
    }

    @Override
    public boolean existsByEmail(String email) {
        return repository.existsByEmail(email);
    }

    @Override
    public Page<UserSummaryItem> searchUsers(SearchUsersQuery query, Integer roleId, Pageable pageable) {
        Map<String, Object> params = new HashMap<>();
        boolean needsProfileJoin = query.fullName() != null && !query.fullName().isBlank();
        String where = buildWhere(query, roleId, params);

        String dataSql = "SELECT u.id AS id, u.email AS email, up.full_name AS fullName,"
                + " up.profile_photo_url AS photoUrl, u.is_banned AS banned, u.deleted_at AS deletedAt,"
                + " u.created_at AS createdAt"
                + " FROM users u"
                + " LEFT JOIN user_profiles up ON up.user_id = u.id"
                + where
                + " ORDER BY " + resolveOrderBy(pageable.getSort());
        Query dataQuery = em.createNativeQuery(dataSql, "UserSummaryMapping");
        params.forEach(dataQuery::setParameter);
        dataQuery.setFirstResult((int) pageable.getOffset());
        dataQuery.setMaxResults(pageable.getPageSize());

        @SuppressWarnings("unchecked")
        List<UserSummaryItem> items = dataQuery.getResultList();

        // El COUNT solo necesita el join a user_profiles si se filtra por nombre.
        String countFrom = " FROM users u"
                + (needsProfileJoin ? " LEFT JOIN user_profiles up ON up.user_id = u.id" : "");
        return PageableExecutionUtils.getPage(items, pageable, () -> {
            Query countQuery = em.createNativeQuery("SELECT COUNT(*)" + countFrom + where);
            params.forEach(countQuery::setParameter);
            return ((Number) countQuery.getSingleResult()).longValue();
        });
    }

    /** Une con AND solo los filtros presentes; devuelve "" si no hay ninguno (sin WHERE 1=1 artificial). */
    private String buildWhere(SearchUsersQuery q, Integer roleId, Map<String, Object> params) {
        List<String> conditions = new ArrayList<>();
        if (q.email() != null && !q.email().isBlank()) {
            conditions.add("u.email LIKE :email");
            params.put("email", "%" + q.email().strip() + "%");
        }
        if (q.fullName() != null && !q.fullName().isBlank()) {
            conditions.add("up.full_name LIKE :fullName");
            params.put("fullName", "%" + q.fullName().strip() + "%");
        }
        if (roleId != null) {
            conditions.add("EXISTS (SELECT 1 FROM user_roles ur WHERE ur.user_id = u.id AND ur.role_id = :roleId)");
            params.put("roleId", roleId);
        }
        if (q.banned() != null) {
            conditions.add("u.is_banned = :banned");
            params.put("banned", q.banned());
        }
        if (q.deleted() != null) {
            conditions.add(q.deleted() ? "u.deleted_at IS NOT NULL" : "u.deleted_at IS NULL");
        }
        return conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);
    }

    /**
     * Resuelve el ORDER BY desde el {@link Sort} del Pageable con whitelist de propiedades para evitar
     * inyeccion. Solo se admiten {@code createdAt} y {@code email}; por defecto created_at DESC.
     */
    private String resolveOrderBy(Sort sort) {
        for (Sort.Order order : sort) {
            String column = switch (order.getProperty()) {
                case "createdAt" -> "u.created_at";
                case "email" -> "u.email";
                default -> null;
            };
            if (column != null) {
                return column + (order.isAscending() ? " ASC" : " DESC");
            }
        }
        return "u.created_at DESC";
    }
}
