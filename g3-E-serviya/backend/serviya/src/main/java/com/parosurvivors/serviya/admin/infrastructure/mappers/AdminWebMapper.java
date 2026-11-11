package com.parosurvivors.serviya.admin.infrastructure.mappers;

import com.parosurvivors.serviya.admin.application.dto.command.CreateUserByAdminCommand;
import com.parosurvivors.serviya.admin.application.dto.command.RemoveFeedbackCommand;
import com.parosurvivors.serviya.admin.application.dto.command.UpdateUserByAdminCommand;
import com.parosurvivors.serviya.admin.application.dto.result.AdminFeedbackSearchResult;
import com.parosurvivors.serviya.admin.application.dto.result.AdminRequestDetailResult;
import com.parosurvivors.serviya.admin.application.dto.result.AdminUserSummaryResult;
import com.parosurvivors.serviya.admin.application.dto.result.UserAdminDetailResult;
import com.parosurvivors.serviya.admin.infrastructure.dto.form.CreateUserByAdminForm;
import com.parosurvivors.serviya.admin.infrastructure.dto.form.RemoveFeedbackForm;
import com.parosurvivors.serviya.admin.infrastructure.dto.form.UpdateUserByAdminForm;
import com.parosurvivors.serviya.admin.infrastructure.dto.response.AdminFeedbackSearchResponse;
import com.parosurvivors.serviya.admin.infrastructure.dto.response.AdminRequestDetailResponse;
import com.parosurvivors.serviya.admin.infrastructure.dto.response.RoleResponse;
import com.parosurvivors.serviya.admin.infrastructure.dto.response.UserAdminDetailResponse;
import com.parosurvivors.serviya.admin.infrastructure.dto.response.UserRoleAssignmentResponse;
import com.parosurvivors.serviya.admin.infrastructure.dto.response.UserSummaryResponse;
import com.parosurvivors.serviya.users.api.dto.RoleSnapshot;
import com.parosurvivors.serviya.users.api.dto.UserRoleAssignmentSnapshot;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * Mapper web (MapStruct) del panel admin: Form->Command y Result/Snapshot->Response.
 */
@Mapper(componentModel = "spring")
public interface AdminWebMapper {

    @Mapping(target = "adminId", source = "adminId")
    CreateUserByAdminCommand toCommand(CreateUserByAdminForm form, Long adminId);

    @Mapping(target = "adminId", source = "adminId")
    RemoveFeedbackCommand toCommand(RemoveFeedbackForm form, Long adminId);

    /** Fila del listado y resultado de creacion: el read-model ya trae nombre y foto (null al crear). */
    UserSummaryResponse toResponse(AdminUserSummaryResult item);

    /** Detalle admin: las metricas snapshot se mapean a las responses locales. */
    @Mapping(target = "clientMetrics.activeRequests", source = "activeRequests")
    UserAdminDetailResponse toResponse(UserAdminDetailResult result);

    AdminRequestDetailResponse toResponse(AdminRequestDetailResult result);

    RoleResponse toRoleResponse(RoleSnapshot role);

    List<RoleResponse> toRoleResponses(List<RoleSnapshot> roles);

    /** RF-068: Form -> Command de edicion de usuario por el admin. */
    UpdateUserByAdminCommand toCommand(UpdateUserByAdminForm form);

    /** RF-067: rol + fecha de concesion. */
    @Mapping(target = "name", source = "roleName")
    UserRoleAssignmentResponse toAssignmentResponse(UserRoleAssignmentSnapshot assignment);

    List<UserRoleAssignmentResponse> toAssignmentResponses(List<UserRoleAssignmentSnapshot> assignments);

    AdminFeedbackSearchResponse toFeedbackResponse(AdminFeedbackSearchResult result);
}
