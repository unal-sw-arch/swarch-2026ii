package com.parosurvivors.serviya.users.infrastructure.adapters.input;

import com.parosurvivors.serviya.users.application.ports.input.UserAuthenticationServicePort;
import com.parosurvivors.serviya.users.application.ports.input.UserDeletionServicePort;
import com.parosurvivors.serviya.users.application.ports.input.UserRoleServicePort;
import com.parosurvivors.serviya.users.application.ports.input.UserServicePort;
import com.parosurvivors.serviya.users.infrastructure.dto.response.AuthResponse;
import com.parosurvivors.serviya.shared.ratelimit.RateLimit;
import com.parosurvivors.serviya.shared.security.CurrentUser;
import com.parosurvivors.serviya.users.infrastructure.adapters.input.api.AccountApi;
import com.parosurvivors.serviya.users.infrastructure.dto.form.ChangeEmailForm;
import com.parosurvivors.serviya.users.infrastructure.dto.form.ChangePasswordForm;
import com.parosurvivors.serviya.users.infrastructure.dto.response.RoleResponse;
import com.parosurvivors.serviya.users.infrastructure.mappers.UserWebMapper;
import com.parosurvivors.serviya.users.infrastructure.repositories.UserRepository;
import com.parosurvivors.serviya.users.infrastructure.dto.response.UserProfileResponse;
import com.parosurvivors.serviya.profiles.infrastructure.repositories.UserProfileRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Adaptador de entrada (REST) de gestion de cuenta del usuario autenticado. Placeholder funcional.
 * Documentacion en {@link AccountApi}. El requesterId se obtendra del JWT (TODO: integrar Spring Security).
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class AccountController implements AccountApi {

    private final UserServicePort userService;
    private final UserDeletionServicePort userDeletionService;
    private final UserRoleServicePort userRoleService;
    /** RF-010/011: adquirir rol re-emite el JWT, por eso pasa por el orquestador de autenticacion. */
    private final UserAuthenticationServicePort authService;
    private final UserWebMapper mapper;
    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;

    /**
     * Obtener información pública de un usuario por ID (acceso sin autenticación).
     */
    @GetMapping("/{userId}")
    public ResponseEntity<UserProfileResponse> getUserProfile(@PathVariable Long userId) {
        var user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        var userProfile = userProfileRepository.findByUserId(userId)
            .orElseThrow(() -> new RuntimeException("Perfil de usuario no encontrado"));
        
        return ResponseEntity.ok(new UserProfileResponse(
            user.getId(),
            userProfile.getFullName(),
            user.getEmail(),
            userProfile.getProfilePhotoUrl(),
            userProfile.getPhoneNumber(),
            userProfile.getBio()
        ));
    }

    @Override
    @PatchMapping("/me/password")
    @RateLimit("account-security")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordForm form) {
        userService.changePassword(mapper.toCommand(form, currentUserId()));
        return ResponseEntity.noContent().build();
    }

    @Override
    @PatchMapping("/me/email")
    @RateLimit("account-security")
    public ResponseEntity<Void> changeEmail(@Valid @RequestBody ChangeEmailForm form) {
        userService.changeEmail(mapper.toCommand(form, currentUserId()));
        return ResponseEntity.noContent().build();
    }

    @Override
    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteOwnAccount() {
        userDeletionService.deleteUser(currentUserId());
        return ResponseEntity.noContent().build();
    }

    /**
     * RF-010. Devuelve un JWT nuevo con el rol ya incluido: los roles viajan como claim del token, asi
     * que sin re-emitirlo el usuario tendria que volver a iniciar sesion para usarlo. El front guarda
     * este token y obtiene "acceso inmediato".
     */
    @Override
    @PostMapping("/me/roles/offerer")
    public ResponseEntity<AuthResponse> acquireOffererRole() {
        return ResponseEntity.ok(mapper.toResponse(authService.acquireRole(currentUserId(), "OFFERER")));
    }

    /** RF-011. Ver nota de {@link #acquireOffererRole()} sobre la re-emision del token. */
    @Override
    @PostMapping("/me/roles/client")
    public ResponseEntity<AuthResponse> acquireClientRole() {
        return ResponseEntity.ok(mapper.toResponse(authService.acquireRole(currentUserId(), "CLIENT")));
    }

    @Override
    @GetMapping("/me/roles")
    public ResponseEntity<List<RoleResponse>> getOwnRoles() {
        return ResponseEntity.ok(mapper.toRoleResponses(userRoleService.getUserRoles(currentUserId())));
    }

    /** Id del usuario autenticado, extraido del JWT por el contexto de seguridad. */
    private Long currentUserId() {
        return CurrentUser.id();
    }
}
