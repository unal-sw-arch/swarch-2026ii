package com.panaderia.identity

import com.fasterxml.jackson.databind.ObjectMapper
import com.panaderia.identity.auth.JwtService
import org.hamcrest.Matchers.hasItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class IdentityApiIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var jwt: JwtService

    @Test
    fun `public registration always creates a cliente`() {
        val body = performJson(
            post("/auth/register"),
            """
            {
              "email": "nuevo@demo.com",
              "password": "Demo1234!",
              "full_name": "Nuevo Cliente",
              "roles": ["admin"]
            }
            """.trimIndent(),
            201,
        )

        assertEquals("cliente", body["user"]["roles"][0].asText())
        val accessToken = body["access_token"].asText()

        mockMvc.perform(
            get("/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.email").value("nuevo@demo.com"))
            .andExpect(jsonPath("$.roles", hasItem("cliente")))
            .andExpect(jsonPath("$.level").value(10))
    }

    @Test
    fun `login returns the union of roles and permissions`() {
        val user = createUser("multi@test.com", listOf("mesero", "despachador"))
        val body = performJson(
            post("/auth/login"),
            """{"email":"multi@test.com","password":"$PASSWORD"}""",
            200,
        )

        val claims = jwt.validate(body["access_token"].asText())
        assertEquals(listOf("despachador", "mesero"), claims.getClaim("roles"))
        assertEquals(50, (claims.getClaim("level") as Number).toInt())
        assertTrue((claims.getClaim("permissions") as Collection<*>).containsAll(listOf("orders:charge", "kitchen:deliver")))
        assertEquals(user.id.toString(), claims.subject)
        assertEquals(50, body["user"]["level"].asInt())
    }

    @Test
    fun `refresh rotates the token and rejects reuse`() {
        createUser("refresh@test.com", listOf("cliente"))
        val login = performJson(
            post("/auth/login"),
            """{"email":"refresh@test.com","password":"$PASSWORD"}""",
            200,
        )
        val originalRefresh = login["refresh_token"].asText()

        val rotated = performJson(
            post("/auth/refresh"),
            """{"refresh_token":"$originalRefresh"}""",
            200,
        )
        assertNotEquals(originalRefresh, rotated["refresh_token"].asText())

        mockMvc.perform(
            post("/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"refresh_token":"$originalRefresh"}"""),
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"))
    }

    @Test
    fun `cliente cannot list users`() {
        val registration = performJson(
            post("/auth/register"),
            """{"email":"solo-cliente@demo.com","password":"Demo1234!","full_name":"Solo Cliente"}""",
            201,
        )
        val token = registration["access_token"].asText()

        mockMvc.perform(get("/users").header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("FORBIDDEN"))
    }

    @Test
    fun `supervisor cannot create admins nor assign roles`() {
        val supervisor = createUser("supervisor@test.com", listOf("supervisor"))
        val token = accessToken(supervisor)

        mockMvc.perform(
            post("/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"email":"otro-admin@test.com","password":"Demo1234!","full_name":"Otro Admin","roles":["admin"]}""",
                ),
        )
            .andExpect(status().isForbidden)

        val operational = createUser("mesero2@test.com", listOf("mesero"))
        mockMvc.perform(
            post("/users/${operational.id}/roles")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"role":"admin"}"""),
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("FORBIDDEN"))
    }

    @Test
    fun `admin creates multi-role users and deactivation blocks login`() {
        val admin = createUser("admin@test.com", listOf("admin"))
        val token = accessToken(admin)

        val created = performJson(
            post("/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $token"),
            """
            {
              "email": "nuevo-mesero@test.com",
              "password": "Demo1234!",
              "full_name": "Nuevo Mesero",
              "roles": ["mesero", "despachador"]
            }
            """.trimIndent(),
            201,
        )
        val userId = created["id"].asText()
        assertEquals(2, created["roles"].size())
        assertEquals(50, created["level"].asInt())

        mockMvc.perform(
            patch("/users/$userId")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"active":false}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.active").value(false))

        mockMvc.perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"nuevo-mesero@test.com","password":"Demo1234!"}"""),
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"))
    }

    @Test
    fun `jwks exposes the public RSA key only`() {
        mockMvc.perform(get("/.well-known/jwks.json"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.keys[0].kty").value("RSA"))
            .andExpect(jsonPath("$.keys[0].n").isNotEmpty())
            .andExpect(jsonPath("$.keys[0].e").isNotEmpty())
            .andExpect(jsonPath("$.keys[0].d").doesNotExist())
    }

    @Test
    fun `admin can read the role and permission matrix`() {
        val admin = createUser("admin-roles@test.com", listOf("admin"))
        val token = accessToken(admin)

        mockMvc.perform(get("/roles").header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[?(@.name == 'admin')].permissions").isNotEmpty())

        mockMvc.perform(get("/permissions").header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[?(@.code == 'orders:create')]").isNotEmpty())
    }

    private fun performJson(
        requestBuilder: org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder,
        body: String,
        expectedStatus: Int,
    ): com.fasterxml.jackson.databind.JsonNode {
        val response = mockMvc.perform(
            requestBuilder
                .contentType(MediaType.APPLICATION_JSON)
                .content(body),
        )
            .andExpect(status().`is`(expectedStatus))
            .andReturn()
            .response
            .contentAsString
        return objectMapper.readTree(response)
    }
}
