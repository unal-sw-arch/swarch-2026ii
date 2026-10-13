package com.parosurvivors.serviya.profiles.application.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.parosurvivors.serviya.profiles.application.dto.command.UpdateProfileCommand;
import com.parosurvivors.serviya.profiles.application.ports.output.AddressPersistencePort;
import com.parosurvivors.serviya.profiles.application.ports.output.UserProfilePersistencePort;
import com.parosurvivors.serviya.profiles.domain.ProfileType;
import com.parosurvivors.serviya.profiles.domain.UserProfile;
import com.parosurvivors.serviya.shared.exceptions.ResourceNotFoundException;
import com.parosurvivors.serviya.shared.textfilter.application.ports.output.WordFilterPort;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** RF-006: modificacion parcial de la informacion personal. */
@ExtendWith(MockitoExtension.class)
class UserProfileServiceTest {

    private static final Long USER_ID = 7L;

    @Mock UserProfilePersistencePort userProfilePersistencePort;
    @Mock AddressPersistencePort addressPersistencePort;
    @Mock WordFilterPort wordFilterPort;

    @InjectMocks UserProfileService service;

    private UserProfile existingProfile() {
        return UserProfile.builder()
                .id(1L).userId(USER_ID)
                .fullName("Ana Gomez")
                .documentType("CC").documentNumber("123456")
                .phoneNumber("3001112233")
                .bio("Bio original")
                .profileType(ProfileType.NATURAL)
                .build();
    }

    /** El PATCH es parcial de verdad: lo que llega null no se toca. */
    @Test
    void solo_actualiza_los_campos_enviados() {
        UserProfile profile = existingProfile();
        when(userProfilePersistencePort.findByUserId(USER_ID)).thenReturn(Optional.of(profile));
        when(userProfilePersistencePort.save(any())).thenAnswer(inv -> inv.getArgument(0));
        // El filtro devuelve el texto tal cual (aqui no hay palabras bloqueadas).
        when(wordFilterPort.filter(any())).thenAnswer(inv -> inv.getArgument(0));

        // Solo se envia el telefono: el resto va null.
        UserProfile updated = service.patchProfile(
                new UpdateProfileCommand(USER_ID, null, "3009998877", null, null));

        assertThat(updated.getPhoneNumber()).isEqualTo("3009998877");
        assertThat(updated.getFullName()).isEqualTo("Ana Gomez");   // intacto
        assertThat(updated.getBio()).isEqualTo("Bio original");     // intacto
    }

    /** RNF-006: el texto libre se censura ANTES de persistir. */
    @Test
    void aplica_el_filtro_de_palabras_a_los_textos_libres() {
        UserProfile profile = existingProfile();
        when(userProfilePersistencePort.findByUserId(USER_ID)).thenReturn(Optional.of(profile));
        when(userProfilePersistencePort.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(wordFilterPort.filter("eres un idiota")).thenReturn("eres un ***");
        when(wordFilterPort.filter(null)).thenReturn(null);

        UserProfile updated = service.patchProfile(
                new UpdateProfileCommand(USER_ID, null, null, null, "eres un idiota"));

        assertThat(updated.getBio()).isEqualTo("eres un ***");
    }

    /**
     * El documento es inmutable: no es que se ignore, es que ni siquiera puede pedirse su cambio
     * (UpdateProfileCommand no tiene esos campos). Esta prueba fija esa garantia.
     */
    @Test
    void nunca_modifica_el_documento() {
        UserProfile profile = existingProfile();
        when(userProfilePersistencePort.findByUserId(USER_ID)).thenReturn(Optional.of(profile));
        when(userProfilePersistencePort.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(wordFilterPort.filter(any())).thenAnswer(inv -> inv.getArgument(0));

        UserProfile updated = service.patchProfile(
                new UpdateProfileCommand(USER_ID, "Ana Maria Gomez", null, null, null));

        assertThat(updated.getDocumentType()).isEqualTo("CC");
        assertThat(updated.getDocumentNumber()).isEqualTo("123456");
    }

    @Test
    void falla_si_el_perfil_no_existe() {
        when(userProfilePersistencePort.findByUserId(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.patchProfile(
                new UpdateProfileCommand(USER_ID, "X", null, null, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
