package com.parosurvivors.serviya.users.api;

import java.util.Optional;

/**
 * Acceso acotado a datos de contacto de identity.
 *
 * <p>Separado de {@link UserQueryApi} para que el correo (PII) solo lo consuma quien realmente lo
 * necesita (por ejemplo, el cliente de notifications al resolver el destinatario de un email).</p>
 */
public interface UserContactApi {

    Optional<String> emailOf(Long userId);
}
