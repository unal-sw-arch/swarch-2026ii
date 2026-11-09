package com.panaderia.identity.config

import com.nimbusds.jose.jwk.RSAKey
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.PosixFilePermissions
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.util.Base64

@ConfigurationProperties(prefix = "identity.jwt")
class JwtProperties {
    var issuer: String = "identity-service"
    var audience: String = "panaderia-express"
    var accessTtlSeconds: Long = 900
    var refreshTtlSeconds: Long = 604800
    var privateKeyPath: String = "/run/secrets/jwt_private.pem"
    var publicKeyPath: String = "/run/secrets/jwt_public.pem"
}

/**
 * Carga el par RSA de desarrollo desde el volumen `identity_keys`.
 * Si los archivos no existen (o no se pueden leer) genera un par nuevo y lo
 * intenta persistir. En producción las claves se montan como secreto.
 */
@Configuration
@EnableConfigurationProperties(JwtProperties::class)
class JwtConfig {
    private val log = LoggerFactory.getLogger(JwtConfig::class.java)

    @Bean
    fun rsaKey(properties: JwtProperties): RSAKey = loadOrCreate(properties)

    private fun loadOrCreate(properties: JwtProperties): RSAKey {
        val privatePath = Path.of(properties.privateKeyPath)
        val publicPath = Path.of(properties.publicKeyPath)

        if (Files.exists(privatePath) && Files.exists(publicPath)) {
            runCatching {
                val pem = Files.readString(privatePath) + "\n" + Files.readString(publicPath)
                val parsed = RSAKey.parseFromPEMEncodedObjects(pem) as RSAKey
                return withKeyId(parsed)
            }.onFailure {
                log.warn("No se pudieron leer las claves JWT; se genera un par nuevo: {}", it.message)
            }
        }

        val generator = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }
        val keyPair = generator.generateKeyPair()
        val privateKey = keyPair.private as RSAPrivateKey
        val publicKey = keyPair.public as RSAPublicKey

        writeKey(privatePath, pem("PRIVATE KEY", privateKey.encoded))
        writeKey(publicPath, pem("PUBLIC KEY", publicKey.encoded))

        return withKeyId(RSAKey.Builder(publicKey).privateKey(privateKey).build())
    }

    /**
     * Garantiza que la clave tenga `kid` estable. El `kid` es el thumbprint
     * RFC 7638 de la clave pública, por lo que es igual antes y después de
     * persistirla en PEM y permite que el JWKS y el JWT lo compartan.
     */
    private fun withKeyId(key: RSAKey): RSAKey {
        if (key.keyID != null) return key
        val builder = RSAKey.Builder(key.toRSAPublicKey())
        key.toRSAPrivateKey()?.let { builder.privateKey(it) }
        return builder.keyID(key.computeThumbprint().toString()).build()
    }

    private fun pem(label: String, bytes: ByteArray): String {
        val body = Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(bytes)
        return "-----BEGIN $label-----\n$body\n-----END $label-----\n"
    }

    private fun writeKey(path: Path, content: String) {
        try {
            path.parent?.let { Files.createDirectories(it) }
            Files.writeString(path, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
            runCatching { Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------")) }
        } catch (ex: Exception) {
            log.warn("No se pudo persistir la clave JWT en {}: {}. Se usará en memoria.", path, ex.message)
        }
    }
}
