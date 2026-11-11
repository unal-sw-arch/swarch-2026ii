package com.panaderia.identity.auth

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class JwksController(
    private val jwtService: JwtService,
) {
    @GetMapping("/.well-known/jwks.json")
    fun jwks(): Map<String, Any> = jwtService.jwks()
}
