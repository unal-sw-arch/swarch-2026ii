package com.parosurvivors.serviya.shared.events.domain;

/**
 * Evento de dominio: a un usuario se le asignó un rol (RF-010/011 auto-asignación, RF-065 por admin).
 *
 * <p>Permite que el módulo de métricas inicialice de forma desacoplada la fila 1-a-1 correspondiente
 * ({@code offerer_metrics} o {@code client_metrics}) sin que el módulo de usuarios llame directamente
 * al servicio de métricas (regla transversal "métricas por eventos").</p>
 *
 * <p>Su listener se procesa en fase {@code BEFORE_COMMIT}, de modo que la inicialización ocurre
 * dentro de la MISMA transacción que la asignación del rol (requisito de atomicidad de RF-010/011).</p>
 *
 * @param userId   usuario que recibe el rol
 * @param roleName nombre del rol asignado (CLIENT, OFFERER, ADMIN)
 */
public record RoleAssignedEvent(Long userId, String roleName) {
}
