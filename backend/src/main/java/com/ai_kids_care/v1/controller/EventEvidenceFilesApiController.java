package com.ai_kids_care.v1.controller;

import com.ai_kids_care.v1.api.EventEvidenceFilesApi;
import com.ai_kids_care.v1.entity.EventEvidenceFile;
import com.ai_kids_care.v1.dto.EventEvidenceFilesCreateRequest;
import com.ai_kids_care.v1.dto.EventEvidenceFilesUpdateRequest;
import com.ai_kids_care.v1.dto.PageOfEventEvidenceFiles;

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
public class EventEvidenceFilesApiController implements EventEvidenceFilesApi {

    /**
     * POST /v1/event_evidence_files : Create event_evidence_files
     *
     * @param eventEvidenceFilesCreateRequest  (required)
     * @return Created (status code 201)
     *         or Bad Request (status code 400)
     *         or Conflict (status code 409)
     *         or Internal Server Error (status code 500)
     * @see EventEvidenceFilesApi#createEventEvidenceFiles
     */
    @Override
    public EventEvidenceFile createEventEvidenceFiles(
        @Parameter(name = "EventEvidenceFilesCreateRequest", description = "", required = true) @RequestBody EventEvidenceFilesCreateRequest eventEvidenceFilesCreateRequest
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

    /**
     * DELETE /v1/event_evidence_files/{evidence_id} : Delete event_evidence_files by evidence_id
     *
     * @param evidenceId  (required)
     * @return No Content (status code 204)
     *         or Not Found (status code 404)
     *         or Internal Server Error (status code 500)
     * @see EventEvidenceFilesApi#deleteEventEvidenceFiles
     */
    @Override
    public void deleteEventEvidenceFiles(
        @Parameter(name = "evidence_id", description = "", required = true, in = ParameterIn.PATH) @PathVariable("evidence_id") Long evidenceId
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

    /**
     * GET /v1/event_evidence_files/{evidence_id} : Get event_evidence_files by evidence_id
     *
     * @param evidenceId  (required)
     * @return OK (status code 200)
     *         or Not Found (status code 404)
     *         or Internal Server Error (status code 500)
     * @see EventEvidenceFilesApi#getEventEvidenceFiles
     */
    @Override
    public EventEvidenceFile getEventEvidenceFiles(
        @Parameter(name = "evidence_id", description = "", required = true, in = ParameterIn.PATH) @PathVariable("evidence_id") Long evidenceId
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

    /**
     * GET /v1/event_evidence_files : List event_evidence_files
     *
     * @param page  (optional)
     * @param size  (optional)
     * @param sort e.g. created_at,desc (optional)
     * @return OK (status code 200)
     *         or Bad Request (status code 400)
     *         or Internal Server Error (status code 500)
     * @see EventEvidenceFilesApi#listEventEvidenceFiles
     */
    @Override
    public PageOfEventEvidenceFiles listEventEvidenceFiles(
        @Parameter(name = "page", description = "", in = ParameterIn.QUERY) @RequestParam(value = "page", required = false) Integer page,
        @Parameter(name = "size", description = "", in = ParameterIn.QUERY) @RequestParam(value = "size", required = false) Integer size,
        @Parameter(name = "sort", description = "e.g. created_at,desc", in = ParameterIn.QUERY) @RequestParam(value = "sort", required = false) String sort
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

    /**
     * PUT /v1/event_evidence_files/{evidence_id} : Update event_evidence_files by evidence_id
     *
     * @param evidenceId  (required)
     * @param eventEvidenceFilesUpdateRequest  (required)
     * @return OK (status code 200)
     *         or Bad Request (status code 400)
     *         or Not Found (status code 404)
     *         or Conflict (status code 409)
     *         or Internal Server Error (status code 500)
     * @see EventEvidenceFilesApi#updateEventEvidenceFiles
     */
    @Override
    public EventEvidenceFile updateEventEvidenceFiles(
        @Parameter(name = "evidence_id", description = "", required = true, in = ParameterIn.PATH) @PathVariable("evidence_id") Long evidenceId,
        @Parameter(name = "EventEvidenceFilesUpdateRequest", description = "", required = true) @RequestBody EventEvidenceFilesUpdateRequest eventEvidenceFilesUpdateRequest
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

}
