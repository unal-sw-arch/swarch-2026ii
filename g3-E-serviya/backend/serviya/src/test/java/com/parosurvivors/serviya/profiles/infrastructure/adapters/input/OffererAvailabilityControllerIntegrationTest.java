package com.parosurvivors.serviya.profiles.infrastructure.adapters.input;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.parosurvivors.serviya.profiles.application.ports.input.OffererAvailabilityServicePort;
import com.parosurvivors.serviya.profiles.infrastructure.mappers.OffererAvailabilityWebMapper;
import com.parosurvivors.serviya.shared.exceptions.UnauthorizedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

/**
 * IDOR (B4): el controller tiene que pasar SIEMPRE la identidad del JWT al servicio.
 * El bug original era que {@code deleteSlot/activateSlot/deactivateSlot} solo pasaban el
 * {@code id} del path, de modo que el servicio no podia comprobar la propiedad.
 */
@WebMvcTest(OffererAvailabilityController.class)
@AutoConfigureMockMvc(addFilters = false)
class OffererAvailabilityControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private OffererAvailabilityServicePort offererAvailabilityService;
    @MockitoBean private OffererAvailabilityWebMapper mapper;

    private static final Long USER_ID = 7L;
    private static final Long SLOT_ID = 10L;

    @BeforeEach
    void setUpSecurityContext() {
        authenticate("ROLE_OFFERER");
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate(String role) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        USER_ID, null, List.of(new SimpleGrantedAuthority(role))));
    }

    @Test
    void deleteSlot_passesJwtIdentityToService() throws Exception {
        mockMvc.perform(delete("/api/v1/offerers/me/availability/slots/" + SLOT_ID))
                .andExpect(status().isNoContent());

        verify(offererAvailabilityService).deleteSlot(SLOT_ID, USER_ID, false);
    }

    @Test
    void activateSlot_passesJwtIdentityToService() throws Exception {
        mockMvc.perform(post("/api/v1/offerers/me/availability/slots/" + SLOT_ID + "/activate"))
                .andExpect(status().isNoContent());

        verify(offererAvailabilityService).activateSlot(SLOT_ID, USER_ID, false);
    }

    @Test
    void deactivateSlot_passesJwtIdentityToService() throws Exception {
        mockMvc.perform(post("/api/v1/offerers/me/availability/slots/" + SLOT_ID + "/deactivate"))
                .andExpect(status().isNoContent());

        verify(offererAvailabilityService).deactivateSlot(SLOT_ID, USER_ID, false);
    }

    @Test
    void deleteSlot_passesAdminFlag_whenCallerIsAdmin() throws Exception {
        authenticate("ROLE_ADMIN");

        mockMvc.perform(delete("/api/v1/offerers/me/availability/slots/" + SLOT_ID))
                .andExpect(status().isNoContent());

        verify(offererAvailabilityService).deleteSlot(SLOT_ID, USER_ID, true);
    }

    @Test
    void deleteSlot_returns401_whenServiceRejectsNonOwner() throws Exception {
        doThrow(new UnauthorizedException("El usuario no es el propietario de la franja horaria"))
                .when(offererAvailabilityService)
                .deleteSlot(anyLong(), anyLong(), anyBoolean());

        mockMvc.perform(delete("/api/v1/offerers/me/availability/slots/" + SLOT_ID))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void activateSlot_returns401_whenServiceRejectsNonOwner() throws Exception {
        doThrow(new UnauthorizedException("El usuario no es el propietario de la franja horaria"))
                .when(offererAvailabilityService)
                .activateSlot(anyLong(), anyLong(), anyBoolean());

        mockMvc.perform(post("/api/v1/offerers/me/availability/slots/" + SLOT_ID + "/activate"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deactivateSlot_returns401_whenServiceRejectsNonOwner() throws Exception {
        doThrow(new UnauthorizedException("El usuario no es el propietario de la franja horaria"))
                .when(offererAvailabilityService)
                .deactivateSlot(anyLong(), anyLong(), anyBoolean());

        mockMvc.perform(post("/api/v1/offerers/me/availability/slots/" + SLOT_ID + "/deactivate"))
                .andExpect(status().isUnauthorized());
    }
}
