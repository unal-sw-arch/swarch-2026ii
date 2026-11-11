package com.panaderia.identity.security

import java.util.UUID

/**
 * Identidad autenticada que viaja en el SecurityContext.
 * Se construye desde el JWT o desde los encabezados que propaga el Gateway.
 */
data class AuthenticatedUser(
    val id: UUID,
    val email: String?,
    val roles: List<String>,
    val permissions: List<String>,
    val level: Int,
) {
    fun has(permission: String): Boolean = permissions.contains(permission)

    fun hasAny(vararg permissions: String): Boolean = permissions.any { it in this.permissions }

    fun hasRole(role: String): Boolean = roles.contains(role)

    fun isAdmin(): Boolean = has("users:manage")
}
