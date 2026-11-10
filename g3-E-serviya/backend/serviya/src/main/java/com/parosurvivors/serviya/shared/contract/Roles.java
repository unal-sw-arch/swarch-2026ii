package com.parosurvivors.serviya.shared.contract;

/**
 * Nombres de rol que cruzan fronteras de modulo.
 *
 * <p>Los contratos entre modulos usan {@code String} en lugar del enum interno
 * {@code users.domain.RoleName}. Asi el contrato es serializable a JSON sin acoplarse al dominio de
 * identity y no obliga a compartir enums entre servicios.</p>
 */
public final class Roles {

    public static final String CLIENT = "CLIENT";
    public static final String OFFERER = "OFFERER";
    public static final String ADMIN = "ADMIN";

    private Roles() {
    }
}
