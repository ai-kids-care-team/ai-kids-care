package com.ai_kids_care.v1.controller;

import com.ai_kids_care.v1.api.AuditLogsApi;
import com.ai_kids_care.v1.entity.AuditLogs;
import com.ai_kids_care.v1.dto.AuditLogsCreateRequest;
import com.ai_kids_care.v1.dto.AuditLogsUpdateRequest;
import com.ai_kids_care.v1.entity.PageOfAuditLogs;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import lombok.RequiredArgsConstructor;


import jakarta.annotation.Generated;

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.20.0")
@RestController
@RequiredArgsConstructor
public class AuditLogsApiController implements AuditLogsApi {

    /**
     * POST /v1/audit_logs : Create audit_logs
     *
     * @param auditLogsCreateRequest  (required)
     * @return Created (status code 201)
     *         or Bad Request (status code 400)
     *         or Conflict (status code 409)
     *         or Internal Server Error (status code 500)
     * @see AuditLogsApi#createAuditLogs
     */
    @Override
    public AuditLogs createAuditLogs(
        @Parameter(name = "AuditLogsCreateRequest", description = "", required = true) @RequestBody AuditLogsCreateRequest auditLogsCreateRequest
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

    /**
     * DELETE /v1/audit_logs/{audit_id} : Delete audit_logs by audit_id
     *
     * @param auditId  (required)
     * @return No Content (status code 204)
     *         or Not Found (status code 404)
     *         or Internal Server Error (status code 500)
     * @see AuditLogsApi#deleteAuditLogs
     */
    @Override
    public void deleteAuditLogs(
        @Parameter(name = "audit_id", description = "", required = true, in = ParameterIn.PATH) @PathVariable("audit_id") Long auditId
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

    /**
     * GET /v1/audit_logs/{audit_id} : Get audit_logs by audit_id
     *
     * @param auditId  (required)
     * @return OK (status code 200)
     *         or Not Found (status code 404)
     *         or Internal Server Error (status code 500)
     * @see AuditLogsApi#getAuditLogs
     */
    @Override
    public AuditLogs getAuditLogs(
        @Parameter(name = "audit_id", description = "", required = true, in = ParameterIn.PATH) @PathVariable("audit_id") Long auditId
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

    /**
     * GET /v1/audit_logs : List audit_logs
     *
     * @param page  (optional)
     * @param size  (optional)
     * @param sort e.g. created_at,desc (optional)
     * @return OK (status code 200)
     *         or Bad Request (status code 400)
     *         or Internal Server Error (status code 500)
     * @see AuditLogsApi#listAuditLogs
     */
    @Override
    public PageOfAuditLogs listAuditLogs(
        @Parameter(name = "page", description = "", in = ParameterIn.QUERY) @RequestParam(value = "page", required = false) Integer page,
        @Parameter(name = "size", description = "", in = ParameterIn.QUERY) @RequestParam(value = "size", required = false) Integer size,
        @Parameter(name = "sort", description = "e.g. created_at,desc", in = ParameterIn.QUERY) @RequestParam(value = "sort", required = false) String sort
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

    /**
     * PUT /v1/audit_logs/{audit_id} : Update audit_logs by audit_id
     *
     * @param auditId  (required)
     * @param auditLogsUpdateRequest  (required)
     * @return OK (status code 200)
     *         or Bad Request (status code 400)
     *         or Not Found (status code 404)
     *         or Conflict (status code 409)
     *         or Internal Server Error (status code 500)
     * @see AuditLogsApi#updateAuditLogs
     */
    @Override
    public AuditLogs updateAuditLogs(
        @Parameter(name = "audit_id", description = "", required = true, in = ParameterIn.PATH) @PathVariable("audit_id") Long auditId,
        @Parameter(name = "AuditLogsUpdateRequest", description = "", required = true) @RequestBody AuditLogsUpdateRequest auditLogsUpdateRequest
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

}
