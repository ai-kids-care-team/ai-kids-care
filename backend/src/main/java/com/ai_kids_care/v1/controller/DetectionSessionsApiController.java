package com.ai_kids_care.v1.controller;

import com.ai_kids_care.v1.api.DetectionSessionsApi;
import com.ai_kids_care.v1.entity.DetectionSessions;
import com.ai_kids_care.v1.dto.DetectionSessionsCreateRequest;
import com.ai_kids_care.v1.dto.DetectionSessionsUpdateRequest;
import com.ai_kids_care.v1.entity.PageOfDetectionSessions;

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
public class DetectionSessionsApiController implements DetectionSessionsApi {

    /**
     * POST /v1/detection_sessions : Create detection_sessions
     *
     * @param detectionSessionsCreateRequest  (required)
     * @return Created (status code 201)
     *         or Bad Request (status code 400)
     *         or Conflict (status code 409)
     *         or Internal Server Error (status code 500)
     * @see DetectionSessionsApi#createDetectionSessions
     */
    @Override
    public DetectionSessions createDetectionSessions(
        @Parameter(name = "DetectionSessionsCreateRequest", description = "", required = true) @RequestBody DetectionSessionsCreateRequest detectionSessionsCreateRequest
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

    /**
     * DELETE /v1/detection_sessions/{session_id} : Delete detection_sessions by session_id
     *
     * @param sessionId  (required)
     * @return No Content (status code 204)
     *         or Not Found (status code 404)
     *         or Internal Server Error (status code 500)
     * @see DetectionSessionsApi#deleteDetectionSessions
     */
    @Override
    public void deleteDetectionSessions(
        @Parameter(name = "session_id", description = "", required = true, in = ParameterIn.PATH) @PathVariable("session_id") Long sessionId
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

    /**
     * GET /v1/detection_sessions/{session_id} : Get detection_sessions by session_id
     *
     * @param sessionId  (required)
     * @return OK (status code 200)
     *         or Not Found (status code 404)
     *         or Internal Server Error (status code 500)
     * @see DetectionSessionsApi#getDetectionSessions
     */
    @Override
    public DetectionSessions getDetectionSessions(
        @Parameter(name = "session_id", description = "", required = true, in = ParameterIn.PATH) @PathVariable("session_id") Long sessionId
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

    /**
     * GET /v1/detection_sessions : List detection_sessions
     *
     * @param page  (optional)
     * @param size  (optional)
     * @param sort e.g. created_at,desc (optional)
     * @return OK (status code 200)
     *         or Bad Request (status code 400)
     *         or Internal Server Error (status code 500)
     * @see DetectionSessionsApi#listDetectionSessions
     */
    @Override
    public PageOfDetectionSessions listDetectionSessions(
        @Parameter(name = "page", description = "", in = ParameterIn.QUERY) @RequestParam(value = "page", required = false) Integer page,
        @Parameter(name = "size", description = "", in = ParameterIn.QUERY) @RequestParam(value = "size", required = false) Integer size,
        @Parameter(name = "sort", description = "e.g. created_at,desc", in = ParameterIn.QUERY) @RequestParam(value = "sort", required = false) String sort
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

    /**
     * PUT /v1/detection_sessions/{session_id} : Update detection_sessions by session_id
     *
     * @param sessionId  (required)
     * @param detectionSessionsUpdateRequest  (required)
     * @return OK (status code 200)
     *         or Bad Request (status code 400)
     *         or Not Found (status code 404)
     *         or Conflict (status code 409)
     *         or Internal Server Error (status code 500)
     * @see DetectionSessionsApi#updateDetectionSessions
     */
    @Override
    public DetectionSessions updateDetectionSessions(
        @Parameter(name = "session_id", description = "", required = true, in = ParameterIn.PATH) @PathVariable("session_id") Long sessionId,
        @Parameter(name = "DetectionSessionsUpdateRequest", description = "", required = true) @RequestBody DetectionSessionsUpdateRequest detectionSessionsUpdateRequest
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

}
