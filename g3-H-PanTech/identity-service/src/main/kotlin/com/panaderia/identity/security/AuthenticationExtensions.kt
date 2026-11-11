package com.panaderia.identity.security

import com.panaderia.identity.web.ApiException
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication

fun Authentication.authenticatedUser(): AuthenticatedUser =
    principal as? AuthenticatedUser
        ?: throw ApiException(
            HttpStatus.UNAUTHORIZED,
            "UNAUTHENTICATED",
            "La petición no tiene una identidad autenticada",
        )
