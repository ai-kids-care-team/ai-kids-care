package com.ai_kids_care.v1.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "AuditLog")
@RestController
@RequestMapping("/api/v1/audit_logs")
public class AuditLogController {
}
