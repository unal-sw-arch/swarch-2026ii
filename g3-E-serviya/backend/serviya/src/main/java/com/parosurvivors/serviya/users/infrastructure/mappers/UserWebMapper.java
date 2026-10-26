package com.parosurvivors.serviya.users.infrastructure.mappers;

import com.parosurvivors.serviya.users.application.dto.command.ChangeEmailCommand;
import com.parosurvivors.serviya.users.application.dto.command.ChangePasswordCommand;
import com.parosurvivors.serviya.users.application.dto.command.ConfirmPasswordResetCommand;
import com.parosurvivors.serviya.users.application.dto.command.LoginCommand;
import com.parosurvivors.serviya.users.application.dto.command.RegisterUserCommand;
import com.parosurvivors.serviya.users.application.dto.command.RequestPasswordResetCommand;
import com.parosurvivors.serviya.users.application.dto.result.AuthResult;
import com.parosurvivors.serviya.users.domain.Role;
import com.parosurvivors.serviya.users.infrastructure.dto.form.ChangeEmailForm;
import com.parosurvivors.serviya.users.infrastructure.dto.form.ChangePasswordForm;
import com.parosurvivors.serviya.users.infrastructure.dto.form.ConfirmPasswordResetForm;
import com.parosurvivors.serviya.users.infrastructure.dto.form.LoginForm;
import com.parosurvivors.serviya.users.infrastructure.dto.form.RegisterUserForm;
import com.parosurvivors.serviya.users.infrastructure.dto.form.RequestPasswordResetForm;
import com.parosurvivors.serviya.users.infrastructure.dto.response.AuthResponse;
import com.parosurvivors.serviya.users.infrastructure.dto.response.RoleResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * Mapper web (MapStruct) del modulo users: traduce entre Forms/Responses (web) y
 * Commands/Results/dominio (aplicacion). No contiene logica de negocio.
 * TODO: completar mapeos donde los nombres de campo difieran.
 */
@Mapper(componentModel = "spring")
public interface UserWebMapper {

    // ---- Form -> Command ----
    RegisterUserCommand toCommand(RegisterUserForm form);

    LoginCommand toCommand(LoginForm form);

    RequestPasswordResetCommand toCommand(RequestPasswordResetForm form);

    ConfirmPasswordResetCommand toCommand(ConfirmPasswordResetForm form);

    @Mapping(target = "userId", source = "userId")
    ChangePasswordCommand toCommand(ChangePasswordForm form, Long userId);

    @Mapping(target = "userId", source = "userId")
    ChangeEmailCommand toCommand(ChangeEmailForm form, Long userId);

    // ---- Result/dominio -> Response ----
    @Mapping(target = "tokenType", constant = "Bearer")
    @Mapping(target = "expiresIn", expression = "java(result.expiresAt() == null ? null "
            + ": java.time.Duration.between(java.time.LocalDateTime.now(), result.expiresAt()).getSeconds())")
    AuthResponse toResponse(AuthResult result);

    RoleResponse toResponse(Role role);

    List<RoleResponse> toRoleResponses(List<Role> roles);
}
