package com.parosurvivors.serviya.users.application.dto.command;

/**
 * Command interno del flujo compartido de creacion de usuario (UserCreationService.createUserAccount),
 * reutilizado por register (visitante) y createUserByAdmin (admin). No proviene directamente de un Form:
 * lo arma el orquestador combinando credenciales + datos de perfil + rol + consentimiento.
 * Sustituye al antiguo placeholder CreateUserData.
 * TODO: revisar campos contra documentacion-BD.docx.
 */
public record CreateUserAccountCommand(
        String email,
        String password,
        String fullName,
        String role,
        String documentType,
        String documentNumber,
        String phone,
        Boolean acceptedTerms,
        /**
         * Direccion principal opcional capturada en el registro. Si viene, se crea en la MISMA
         * transaccion y queda como direccion principal del perfil. Los cuatro campos van juntos:
         * la tabla addresses exige coordenadas (lat/lng NOT NULL), asi que una linea de direccion
         * sin coordenadas no es persistible.
         */
        String addressLine,
        String city,
        java.math.BigDecimal latitude,
        java.math.BigDecimal longitude) {

    /** true solo si llego el bloque de direccion COMPLETO (linea + ciudad + coordenadas). */
    public boolean hasAddress() {
        return addressLine != null && !addressLine.isBlank()
                && city != null && !city.isBlank()
                && latitude != null && longitude != null;
    }
}
