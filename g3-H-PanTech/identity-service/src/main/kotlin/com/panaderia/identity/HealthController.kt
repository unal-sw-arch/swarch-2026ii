package com.panaderia.identity

import org.springframework.beans.factory.ObjectProvider
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import javax.sql.DataSource

@RestController
class HealthController(
    private val dataSource: DataSource,
    private val redis: ObjectProvider<StringRedisTemplate>,
) {
    @GetMapping("/health")
    fun health(): Map<String, String> = mapOf("status" to "ok")

    @GetMapping("/ready")
    fun ready(): ResponseEntity<Map<String, Any>> {
        val dependencies = linkedMapOf<String, Any>()
        var ready = true

        try {
            dataSource.connection.use { connection ->
                connection.createStatement().use { statement -> statement.execute("SELECT 1") }
            }
            dependencies["postgres"] = "ok"
        } catch (ex: Exception) {
            ready = false
            dependencies["postgres"] = "down"
        }

        try {
            val template = redis.ifAvailable
            if (template == null) {
                dependencies["redis"] = "not_configured"
            } else {
                val factory = template.connectionFactory
                    ?: throw IllegalStateException("Redis sin connection factory")
                factory.connection.use { it.ping() }
                dependencies["redis"] = "ok"
            }
        } catch (ex: Exception) {
            // Redis es una caché opcional: PostgreSQL sigue siendo la fuente de
            // verdad, así que su caída no marca al servicio como no listo.
            dependencies["redis"] = "down"
        }

        return if (ready) {
            ResponseEntity.ok(mapOf("status" to "ready", "dependencies" to dependencies))
        } else {
            ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(mapOf("status" to "not_ready", "dependencies" to dependencies))
        }
    }
}
