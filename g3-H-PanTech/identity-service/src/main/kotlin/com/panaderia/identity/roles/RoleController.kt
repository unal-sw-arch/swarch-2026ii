package com.panaderia.identity.roles

import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class RoleController(
    private val roleService: RoleService,
) {
    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('roles:manage')")
    fun roles(): List<RoleResponse> = roleService.listRoles()

    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority('roles:manage')")
    fun permissions(): List<PermissionResponse> = roleService.listPermissions()
}
