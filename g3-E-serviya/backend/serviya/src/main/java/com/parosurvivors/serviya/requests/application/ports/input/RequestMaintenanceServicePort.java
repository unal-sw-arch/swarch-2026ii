package com.parosurvivors.serviya.requests.application.ports.input;

/**
 * Puerto de entrada de RequestMaintenanceService — casos de uso disparados por un
 * adaptador @Scheduled (tareas de mantenimiento por tiempo).
 * Ver documents/project-structure/estructura-servicios.docx (módulo 4).
 */
public interface RequestMaintenanceServicePort {

    void rejectExpiredPendingRequests();

    void markStaleAcceptedAsNotProvided();

    void rejectExpiredProposals();

    void finalizeUnconfirmedCompletions();
}
