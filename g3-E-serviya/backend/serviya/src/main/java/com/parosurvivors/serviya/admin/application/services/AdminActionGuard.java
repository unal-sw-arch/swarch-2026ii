package com.parosurvivors.serviya.admin.application.services;

import com.parosurvivors.serviya.shared.contract.Roles;
import com.parosurvivors.serviya.shared.exceptions.InvalidStateException;
import com.parosurvivors.serviya.users.api.UserQueryApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Guardas del panel admin (BUG B5): centraliza las dos invariantes que evitan que un administrador
 * deje al sistema sin administradores operativos.
 * <ul>
 *   <li><b>Auto-accion:</b> un admin no puede ejecutar acciones destructivas sobre su propia cuenta
 *       (auto-ban / auto-delete). Si se banea o se elimina a si mismo, el filtro JWT corta su sesion
 *       al instante (revalida {@code isActive()} en cada request).</li>
 *   <li><b>Ultimo admin:</b> una accion que quita el rol ADMIN (o deja la cuenta sin poder operar) no
 *       puede dejar al sistema sin, al menos, otro administrador activo (no baneado ni eliminado).</li>
 * </ul>
 * Recibe {@code adminId} siempre desde el JWT ({@code CurrentUser.id()}), nunca del body/path.
 */
@Component
@RequiredArgsConstructor
public class AdminActionGuard {

    private final UserQueryApi userQueryApi;

    /** Rechaza que un admin ejecute una accion destructiva sobre su propia cuenta. */
    public void requireNotSelf(Long adminId, Long targetUserId) {
        if (adminId != null && adminId.equals(targetUserId)) {
            throw new InvalidStateException(
                    "No puedes realizar esta acción sobre tu propia cuenta de administrador");
        }
    }

    /**
     * Rechaza una accion que dejaria al sistema sin ningun administrador activo. Si el objetivo no
     * tiene el rol ADMIN, la accion no puede provocar lockout y este guard no hace nada.
     */
    public void requireAnotherActiveAdmin(Long targetUserId) {
        if (!userQueryApi.hasRole(targetUserId, Roles.ADMIN)) {
            return;
        }
        boolean anotherActiveAdmin = userQueryApi.findUserIdsByRole(Roles.ADMIN).stream()
                .filter(id -> !id.equals(targetUserId))
                .anyMatch(this::isActiveUser);
        if (!anotherActiveAdmin) {
            throw new InvalidStateException(
                    "La acción dejaría al sistema sin ningún administrador activo");
        }
    }

    private boolean isActiveUser(Long userId) {
        return userQueryApi.findById(userId)
                .map(user -> !user.banned() && !user.deleted())
                .orElse(false);
    }
}
