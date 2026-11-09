package com.panaderia.identity.web

import org.springframework.http.HttpStatus

class ApiException(
    val status: HttpStatus,
    val code: String,
    override val message: String,
    val details: Map<String, Any?> = emptyMap(),
) : RuntimeException(message)

data class ErrorResponse(
    val code: String,
    val message: String,
    val request_id: String,
    val details: Map<String, Any?> = emptyMap(),
)
