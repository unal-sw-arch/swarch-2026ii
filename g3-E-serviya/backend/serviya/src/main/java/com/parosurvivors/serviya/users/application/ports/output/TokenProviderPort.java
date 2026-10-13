package com.parosurvivors.serviya.users.application.ports.output;

import com.parosurvivors.serviya.users.application.dto.result.IssuedToken;
import com.parosurvivors.serviya.users.domain.RoleName;

import java.util.List;

/**
 * Puerto de salida para emitir tokens de autenticacion. La capa de aplicacion (login/registro)
 * depende de esta abstraccion; el detalle concreto (JWT con jjwt) vive en infraestructura
 * ({@code JwtService}). Mantiene la regla de dependencias hexagonal: aplicacion no conoce JWT.
 */
public interface TokenProviderPort {

    IssuedToken issue(Long userId, List<RoleName> roles);
}
