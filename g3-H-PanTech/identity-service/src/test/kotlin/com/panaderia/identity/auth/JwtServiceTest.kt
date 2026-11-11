package com.panaderia.identity.auth

import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.panaderia.identity.config.JwtProperties
import com.panaderia.identity.domain.Permission
import com.panaderia.identity.domain.Role
import com.panaderia.identity.domain.User
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class JwtServiceTest {
    private val rsaKey = RSAKeyGenerator(2048).keyID("test-key").generate()

    @Test
    fun `emits the required claims`() {
        val properties = JwtProperties().apply {
            issuer = "identity-service"
            audience = "panaderia-express"
            accessTtlSeconds = 900
        }
        val service = JwtService(properties, rsaKey)
        val user = userWithRoles(
            Role(
                name = "mesero",
                level = 50,
                isStaff = true,
                permissions = mutableSetOf(
                    Permission(code = "orders:create", description = "crear"),
                    Permission(code = "orders:charge", description = "cobrar"),
                ),
            ),
            Role(
                name = "despachador",
                level = 50,
                isStaff = true,
                permissions = mutableSetOf(Permission(code = "kitchen:deliver", description = "entregar")),
            ),
        )

        val token = service.issueAccessToken(user)
        val claims = service.validate(token.value)

        assertEquals(user.id.toString(), claims.subject)
        assertEquals("identity-service", claims.issuer)
        assertEquals(listOf("panaderia-express"), claims.audience)
        assertEquals(listOf("despachador", "mesero"), claims.getClaim("roles"))
        assertEquals(50, (claims.getClaim("level") as Number).toInt())
        assertTrue((claims.getClaim("permissions") as Collection<*>).containsAll(listOf("orders:create", "orders:charge", "kitchen:deliver")))
        assertTrue(claims.expirationTime.toInstant().isAfter(claims.issueTime.toInstant()))
    }

    @Test
    fun `rejects an expired token`() {
        val properties = JwtProperties().apply {
            issuer = "identity-service"
            audience = "panaderia-express"
            accessTtlSeconds = -5
        }
        val service = JwtService(properties, rsaKey)
        val token = service.issueAccessToken(userWithRoles(Role(name = "cliente", level = 10)))

        assertThrows(InvalidTokenException::class.java) { service.validate(token.value) }
    }

    @Test
    fun `jwks never exposes the private key`() {
        val service = JwtService(JwtProperties(), rsaKey)
        val keys = service.jwks()["keys"] as List<*>
        val key = keys.single() as Map<*, *>

        assertEquals("RSA", key["kty"])
        assertTrue(key.containsKey("n"))
        assertTrue(key.containsKey("e"))
        assertTrue(!key.containsKey("d"))
    }

    private fun userWithRoles(vararg roles: Role): User =
        User(
            id = UUID.randomUUID(),
            email = "demo@panaderia.com",
            passwordHash = "hash",
            fullName = "Demo",
            roles = roles.toMutableSet(),
        )
}
