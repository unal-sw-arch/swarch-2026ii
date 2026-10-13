package com.parosurvivors.serviya.users.infrastructure.adapters.input;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.parosurvivors.serviya.users.application.dto.command.ChangePasswordCommand;
import com.parosurvivors.serviya.users.application.ports.input.UserAuthenticationServicePort;
import com.parosurvivors.serviya.users.application.ports.input.UserDeletionServicePort;
import com.parosurvivors.serviya.users.application.ports.input.UserRoleServicePort;
import com.parosurvivors.serviya.users.application.ports.input.UserServicePort;
import com.parosurvivors.serviya.users.infrastructure.dto.form.ChangePasswordForm;
import com.parosurvivors.serviya.users.infrastructure.mappers.UserWebMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

/**
 * RF-007: integration test basico del endpoint PATCH /api/v1/users/me/password.
 * Verifica que el controller recibe el JSON, extrae el userId del JWT (SecurityContext)
 * y delega en el servicio.
 */
@WebMvcTest(AccountController.class)
@AutoConfigureMockMvc(addFilters = false)
class AccountControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private UserServicePort userService;
    @MockitoBean private UserDeletionServicePort userDeletionService;
    @MockitoBean private UserRoleServicePort userRoleService;
    /** RF-010/011: adquirir un rol re-emite el JWT, por eso AccountController depende del orquestador de auth. */
    @MockitoBean private UserAuthenticationServicePort authService;
    @MockitoBean private UserWebMapper mapper;
    /** AccountController tambien consulta estos repositorios (GET /users/{id}); el slice web no los crea. */
    @MockitoBean private com.parosurvivors.serviya.users.infrastructure.repositories.UserRepository userRepository;
    @MockitoBean private com.parosurvivors.serviya.profiles.infrastructure.repositories.UserProfileRepository userProfileRepository;

    private static final Long USER_ID = 1L;

    @BeforeEach
    void setUpSecurityContext() {
        var auth = new UsernamePasswordAuthenticationToken(
                USER_ID, null, List.of(new SimpleGrantedAuthority("ROLE_CLIENT")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    void changePassword_returns204_whenValid() throws Exception {
        when(mapper.toCommand(any(ChangePasswordForm.class), anyLong()))
                .thenReturn(new ChangePasswordCommand(USER_ID, "oldPass123", "newPass456"));

        mockMvc.perform(patch("/api/v1/users/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"oldPass123\",\"newPassword\":\"newPass456\"}"))
                .andExpect(status().isNoContent());

        verify(userService).changePassword(any(ChangePasswordCommand.class));
    }

    @Test
    void changePassword_returns400_whenCurrentPasswordBlank() throws Exception {
        mockMvc.perform(patch("/api/v1/users/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"\",\"newPassword\":\"newPass456\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void changePassword_returns400_whenNewPasswordTooShort() throws Exception {
        mockMvc.perform(patch("/api/v1/users/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"oldPass123\",\"newPassword\":\"short\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void changePassword_returns400_whenBodyMissing() throws Exception {
        mockMvc.perform(patch("/api/v1/users/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
