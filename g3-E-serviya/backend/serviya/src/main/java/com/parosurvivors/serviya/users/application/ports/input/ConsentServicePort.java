package com.parosurvivors.serviya.users.application.ports.input;

import com.parosurvivors.serviya.users.domain.Consent;

/**
 * Puerto de entrada de ConsentService.
 * Ver documents/project-structure/estructura-servicios.docx (módulo 1).
 */
public interface ConsentServicePort {

    Consent createConsent(Long userId, boolean accepted);

    boolean hasConsented(Long userId);
}
