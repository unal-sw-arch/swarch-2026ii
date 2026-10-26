package com.parosurvivors.serviya.users.application.ports.input;

/**
 * Puerto de entrada de UserDeletionService — orquesta la eliminación (soft delete)
 * de un usuario que puede ser oferente y/o cliente con solicitudes activas.
 * Ver documents/project-structure/estructura-servicios.docx (módulo 1).
 */
public interface UserDeletionServicePort {

    void deleteUser(Long userId);
}
