package com.panaderia.identity.users

import com.panaderia.identity.security.authenticatedUser
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/users")
class UserController(
    private val userService: UserService,
) {
    @GetMapping
    @PreAuthorize("hasAnyAuthority('users:manage', 'users:manage_operational')")
    fun list(authentication: Authentication): List<UserResponse> =
        userService.list(authentication.authenticatedUser())

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('users:manage', 'users:manage_operational')")
    fun get(@PathVariable id: UUID, authentication: Authentication): UserResponse =
        userService.get(authentication.authenticatedUser(), id)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('users:manage', 'users:manage_operational')")
    fun create(
        @Valid @RequestBody request: UserCreateRequest,
        authentication: Authentication,
    ): UserResponse = userService.create(authentication.authenticatedUser(), request)

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('users:manage', 'users:manage_operational')")
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UserUpdateRequest,
        authentication: Authentication,
    ): UserResponse = userService.update(authentication.authenticatedUser(), id, request)

    @PostMapping("/{id}/roles")
    @PreAuthorize("hasAuthority('roles:manage')")
    fun assignRole(
        @PathVariable id: UUID,
        @Valid @RequestBody request: RoleAssignmentRequest,
        authentication: Authentication,
    ): UserResponse = userService.assignRole(authentication.authenticatedUser(), id, request.role)

    @DeleteMapping("/{id}/roles/{role}")
    @PreAuthorize("hasAuthority('roles:manage')")
    fun removeRole(
        @PathVariable id: UUID,
        @PathVariable role: String,
        authentication: Authentication,
    ): UserResponse = userService.removeRole(authentication.authenticatedUser(), id, role)
}
