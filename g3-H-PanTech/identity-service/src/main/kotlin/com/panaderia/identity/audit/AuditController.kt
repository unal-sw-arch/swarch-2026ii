package com.panaderia.identity.audit

import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/audit")
class AuditController(
    private val auditService: AuditService,
) {
    @GetMapping
    @PreAuthorize("hasAuthority('users:manage')")
    fun list(
        @RequestParam(defaultValue = "100") limit: Int,
        @RequestParam(required = false) action: String?,
    ): List<AuditLogResponse> = auditService.list(limit, action)
}
