package com.panaderia.identity

import com.panaderia.identity.auth.JwtService
import com.panaderia.identity.domain.Permission
import com.panaderia.identity.domain.Role
import com.panaderia.identity.domain.User
import com.panaderia.identity.repository.PermissionRepository
import com.panaderia.identity.repository.RoleRepository
import com.panaderia.identity.repository.UserRepository
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
abstract class IntegrationTestBase {
    @Autowired
    protected lateinit var mockMvc: MockMvc

    @Autowired
    protected lateinit var userRepository: UserRepository

    @Autowired
    protected lateinit var roleRepository: RoleRepository

    @Autowired
    protected lateinit var permissionRepository: PermissionRepository

    @Autowired
    protected lateinit var passwordEncoder: PasswordEncoder

    @Autowired
    protected lateinit var jwtService: JwtService

    @BeforeEach
    fun seedAuthorizationModel() {
        if (roleRepository.count() > 0) return

        val codes = listOf(
            "orders:create_own", "orders:read_own", "orders:create", "orders:charge",
            "orders:read_all", "reservations:manage_own", "reservations:manage_all",
            "kitchen:read", "kitchen:update", "kitchen:deliver", "products:manage",
            "history:read_own", "history:read_all", "reports:read", "users:manage",
            "users:manage_operational", "roles:manage",
        )
        val permissions = codes.associateWith { code ->
            permissionRepository.save(Permission(code = code, description = code))
        }

        fun role(name: String, level: Int, staff: Boolean, granted: List<String>) {
            roleRepository.save(
                Role(
                    name = name,
                    description = name,
                    level = level,
                    isStaff = staff,
                    permissions = granted.mapNotNull { permissions[it] }.toMutableSet(),
                ),
            )
        }

        role("cliente", 10, false, listOf("orders:create_own", "orders:read_own", "reservations:manage_own", "history:read_own"))
        role("mesero", 50, true, listOf("orders:create", "orders:charge", "reservations:manage_all"))
        role("cocinero", 50, true, listOf("kitchen:read", "kitchen:update"))
        role("despachador", 50, true, listOf("kitchen:read", "kitchen:deliver"))
        role(
            "supervisor",
            80,
            true,
            listOf(
                "orders:create", "orders:charge", "orders:read_all", "reservations:manage_all",
                "kitchen:read", "kitchen:update", "kitchen:deliver", "history:read_all",
                "reports:read", "users:manage_operational",
            ),
        )
        role("admin", 100, true, codes)
    }

    protected fun createUser(
        email: String,
        roles: List<String>,
        active: Boolean = true,
    ): User {
        val roleEntities = roleRepository.findAllByNameIn(roles).toMutableSet()
        return userRepository.save(
            User(
                email = email,
                passwordHash = passwordEncoder.encode(PASSWORD),
                fullName = email.substringBefore('@'),
                active = active,
                roles = roleEntities,
            ),
        )
    }

    protected fun accessToken(user: User): String = jwtService.issueAccessToken(user).value

    companion object {
        const val PASSWORD = "Demo1234!"
    }
}
