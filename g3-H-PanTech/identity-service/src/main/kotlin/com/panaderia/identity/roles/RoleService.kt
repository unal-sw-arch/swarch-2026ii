package com.panaderia.identity.roles

import com.panaderia.identity.repository.PermissionRepository
import com.panaderia.identity.repository.RoleRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

data class RoleResponse(
    val id: UUID,
    val name: String,
    val description: String,
    val level: Int,
    val is_staff: Boolean,
    val permissions: List<String>,
)

data class PermissionResponse(
    val code: String,
    val description: String,
)

@Service
class RoleService(
    private val roleRepository: RoleRepository,
    private val permissionRepository: PermissionRepository,
) {
    @Transactional(readOnly = true)
    fun listRoles(): List<RoleResponse> =
        roleRepository.findAllByOrderByLevelDescNameAsc().map { role ->
            RoleResponse(
                id = requireNotNull(role.id),
                name = role.name,
                description = role.description,
                level = role.level,
                is_staff = role.isStaff,
                permissions = role.permissionCodes(),
            )
        }

    @Transactional(readOnly = true)
    fun listPermissions(): List<PermissionResponse> =
        permissionRepository.findAllByOrderByCodeAsc().map { permission ->
            PermissionResponse(code = permission.code, description = permission.description)
        }
}
