package com.panaderia.identity.web

import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

@RestControllerAdvice
class GlobalExceptionHandler {
    private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(ApiException::class)
    fun handleApi(ex: ApiException, request: HttpServletRequest): ResponseEntity<ErrorResponse> =
        response(ex.status, ex.code, ex.message, ex.details, request)

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(
        ex: MethodArgumentNotValidException,
        request: HttpServletRequest,
    ): ResponseEntity<ErrorResponse> {
        val fields = ex.bindingResult.fieldErrors.associate { field ->
            field.field to (field.defaultMessage ?: "Valor inválido")
        }
        return response(
            HttpStatus.BAD_REQUEST,
            "VALIDATION_ERROR",
            "El cuerpo de la petición no es válido",
            fields,
            request,
        )
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleUnreadable(request: HttpServletRequest): ResponseEntity<ErrorResponse> =
        response(
            HttpStatus.BAD_REQUEST,
            "VALIDATION_ERROR",
            "El cuerpo de la petición no se pudo interpretar",
            emptyMap(),
            request,
        )

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatch(ex: MethodArgumentTypeMismatchException, request: HttpServletRequest): ResponseEntity<ErrorResponse> =
        response(
            HttpStatus.BAD_REQUEST,
            "VALIDATION_ERROR",
            "El parámetro '${ex.name}' no es válido",
            emptyMap(),
            request,
        )

    @ExceptionHandler(AccessDeniedException::class)
    fun handleDenied(request: HttpServletRequest): ResponseEntity<ErrorResponse> =
        response(
            HttpStatus.FORBIDDEN,
            "FORBIDDEN",
            "El rol o permiso no alcanza para esta operación",
            emptyMap(),
            request,
        )

    @ExceptionHandler(AuthenticationException::class)
    fun handleAuthentication(request: HttpServletRequest): ResponseEntity<ErrorResponse> =
        response(
            HttpStatus.UNAUTHORIZED,
            "UNAUTHENTICATED",
            "Falta la autenticación o el token está vencido",
            emptyMap(),
            request,
        )

    @ExceptionHandler(DataIntegrityViolationException::class)
    fun handleConflict(ex: DataIntegrityViolationException, request: HttpServletRequest): ResponseEntity<ErrorResponse> {
        log.warn("Conflicto de integridad: {}", ex.mostSpecificCause.message)
        return response(
            HttpStatus.CONFLICT,
            "INVALID_STATE",
            "La operación entra en conflicto con el estado actual",
            emptyMap(),
            request,
        )
    }

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(ex: Exception, request: HttpServletRequest): ResponseEntity<ErrorResponse> {
        log.error("Error no controlado", ex)
        return response(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "INTERNAL_ERROR",
            "Ocurrió un error inesperado",
            emptyMap(),
            request,
        )
    }

    private fun response(
        status: HttpStatus,
        code: String,
        message: String,
        details: Map<String, Any?>,
        request: HttpServletRequest,
    ): ResponseEntity<ErrorResponse> =
        ResponseEntity
            .status(status)
            .body(ErrorResponse(code, message, RequestIdFilter.of(request), details))
}
