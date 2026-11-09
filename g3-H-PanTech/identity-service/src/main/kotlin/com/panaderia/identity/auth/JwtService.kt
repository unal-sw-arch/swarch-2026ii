package com.panaderia.identity.auth

import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.crypto.RSASSAVerifier
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import com.panaderia.identity.config.JwtProperties
import com.panaderia.identity.domain.User
import com.panaderia.identity.security.AuthenticatedUser
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.Date
import java.util.UUID

data class IssuedToken(
    val value: String,
    val expiresAt: Instant,
    val expiresInSeconds: Long,
)

class InvalidTokenException(message: String) : RuntimeException(message)

@Service
class JwtService(
    private val properties: JwtProperties,
    private val rsaKey: RSAKey,
) {
    fun issueAccessToken(user: User): IssuedToken {
        val userId = requireNotNull(user.id) { "El usuario debe estar persistido para emitir el JWT" }
        val now = Instant.now()
        val expiresAt = now.plusSeconds(properties.accessTtlSeconds)

        val claims = JWTClaimsSet.Builder()
            .subject(userId.toString())
            .claim("email", user.email)
            .claim("roles", user.roleNames())
            .claim("permissions", user.effectivePermissions().toList())
            .claim("level", user.effectiveLevel())
            .issuer(properties.issuer)
            .audience(properties.audience)
            .issueTime(Date.from(now))
            .expirationTime(Date.from(expiresAt))
            .build()

        val header = JWSHeader.Builder(JWSAlgorithm.RS256)
            .keyID(rsaKey.keyID)
            .type(JOSEObjectType.JWT)
            .build()

        val signed = SignedJWT(header, claims)
        signed.sign(RSASSASigner(rsaKey))

        return IssuedToken(signed.serialize(), expiresAt, properties.accessTtlSeconds)
    }

    fun validate(token: String): JWTClaimsSet {
        val jwt = try {
            SignedJWT.parse(token)
        } catch (ex: Exception) {
            throw InvalidTokenException("El token no tiene un formato válido")
        }

        val verifier = RSASSAVerifier(rsaKey.toRSAPublicKey())
        if (!jwt.verify(verifier)) {
            throw InvalidTokenException("La firma del token no es válida")
        }

        val claims = jwt.jwtClaimsSet
        if (claims.issuer != properties.issuer) {
            throw InvalidTokenException("El emisor del token no es válido")
        }
        if (claims.audience == null || !claims.audience.contains(properties.audience)) {
            throw InvalidTokenException("La audiencia del token no es válida")
        }
        val expiration = claims.expirationTime?.toInstant()
        if (expiration == null || !expiration.isAfter(Instant.now())) {
            throw InvalidTokenException("El token está vencido")
        }
        return claims
    }

    fun principal(claims: JWTClaimsSet): AuthenticatedUser {
        val subject = claims.subject ?: throw InvalidTokenException("El token no tiene sujeto")
        val userId = try {
            UUID.fromString(subject)
        } catch (ex: IllegalArgumentException) {
            throw InvalidTokenException("El sujeto del token no es un UUID")
        }
        return AuthenticatedUser(
            id = userId,
            email = claims.getStringClaim("email"),
            roles = stringListClaim(claims, "roles"),
            permissions = stringListClaim(claims, "permissions"),
            level = (claims.getClaim("level") as? Number)?.toInt() ?: 0,
        )
    }

    fun jwks(): Map<String, Any> =
        mapOf("keys" to listOf(rsaKey.toPublicJWK().toJSONObject()))

    private fun stringListClaim(claims: JWTClaimsSet, name: String): List<String> =
        (claims.getClaim(name) as? Collection<*>)?.filterIsInstance<String>() ?: emptyList()
}
