package com.parosurvivors.gateway.error;

/**
 * Catálogo de códigos de error del gateway (P3.5 / contrato 4.4). El cliente decide por el {@code code}:
 * los {@code TOKEN_*} y {@code RATE_LIMITED} son 401/429 → el interceptor del frontend refresca o reintenta;
 * {@code FORBIDDEN} es 403 → <b>no</b> debe refrescar, solo mostrar "sin permiso".
 */
public enum GatewayErrorCode {

    /** 401: no llegó ningún token (falta la cabecera Authorization). */
    TOKEN_MISSING,
    /** 401: el access token caducó (el cliente debe refrescar). */
    TOKEN_EXPIRED,
    /** 401: token inválido (firma, iss/aud, algoritmo, malformado). */
    TOKEN_INVALID,
    /** 401: token revocado por {@code min_iat} (sesión cerrada por ban/rol/contraseña/logout-all). */
    TOKEN_REVOKED,
    /** 403: autenticado pero sin el rol necesario. */
    FORBIDDEN,
    /** 429: cuota de rate limiting superada. */
    RATE_LIMITED
}
