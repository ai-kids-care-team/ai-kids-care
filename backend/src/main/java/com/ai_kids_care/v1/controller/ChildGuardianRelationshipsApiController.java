package com.ai_kids_care.v1.controller;

import com.ai_kids_care.v1.api.ChildGuardianRelationshipsApi;
import com.ai_kids_care.v1.entity.ChildGuardianRelationships;
import com.ai_kids_care.v1.dto.ChildGuardianRelationshipsCreateRequest;
import com.ai_kids_care.v1.dto.PageOfChildGuardianRelationships;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import lombok.RequiredArgsConstructor;


import jakarta.annotation.Generated;

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.20.0")
@RestController
@RequiredArgsConstructor
public class ChildGuardianRelationshipsApiController implements ChildGuardianRelationshipsApi {

    /**
     * POST /v1/child_guardian_relationships : Create child_guardian_relationships
     *
     * @param childGuardianRelationshipsCreateRequest  (required)
     * @return Created (status code 201)
     *         or Bad Request (status code 400)
     *         or Conflict (status code 409)
     *         or Internal Server Error (status code 500)
     * @see ChildGuardianRelationshipsApi#createChildGuardianRelationships
     */
    @Override
    public ChildGuardianRelationships createChildGuardianRelationships(
        @Parameter(name = "ChildGuardianRelationshipsCreateRequest", description = "", required = true) @RequestBody ChildGuardianRelationshipsCreateRequest childGuardianRelationshipsCreateRequest
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

    /**
     * DELETE /v1/child_guardian_relationships/by-key : Delete child_guardian_relationships by kindergarten_id,child_id,guardian_id
     *
     * @param kindergartenId  (required)
     * @param childId  (required)
     * @param guardianId  (required)
     * @return No Content (status code 204)
     *         or Not Found (status code 404)
     *         or Internal Server Error (status code 500)
     * @see ChildGuardianRelationshipsApi#deleteChildGuardianRelationshipsByKey
     */
    @Override
    public void deleteChildGuardianRelationshipsByKey(
        @Parameter(name = "kindergarten_id", description = "", required = true, in = ParameterIn.QUERY) @RequestParam(value = "kindergarten_id", required = true) Long kindergartenId,
        @Parameter(name = "child_id", description = "", required = true, in = ParameterIn.QUERY) @RequestParam(value = "child_id", required = true) Long childId,
        @Parameter(name = "guardian_id", description = "", required = true, in = ParameterIn.QUERY) @RequestParam(value = "guardian_id", required = true) Long guardianId
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

    /**
     * GET /v1/child_guardian_relationships/by-key : Get child_guardian_relationships by kindergarten_id,child_id,guardian_id
     *
     * @param kindergartenId  (required)
     * @param childId  (required)
     * @param guardianId  (required)
     * @return OK (status code 200)
     *         or Not Found (status code 404)
     *         or Internal Server Error (status code 500)
     * @see ChildGuardianRelationshipsApi#getChildGuardianRelationshipsByKey
     */
    @Override
    public ChildGuardianRelationships getChildGuardianRelationshipsByKey(
        @Parameter(name = "kindergarten_id", description = "", required = true, in = ParameterIn.QUERY) @RequestParam(value = "kindergarten_id", required = true) Long kindergartenId,
        @Parameter(name = "child_id", description = "", required = true, in = ParameterIn.QUERY) @RequestParam(value = "child_id", required = true) Long childId,
        @Parameter(name = "guardian_id", description = "", required = true, in = ParameterIn.QUERY) @RequestParam(value = "guardian_id", required = true) Long guardianId
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

    /**
     * GET /v1/child_guardian_relationships : List child_guardian_relationships
     *
     * @param page  (optional)
     * @param size  (optional)
     * @param sort e.g. created_at,desc (optional)
     * @return OK (status code 200)
     *         or Bad Request (status code 400)
     *         or Internal Server Error (status code 500)
     * @see ChildGuardianRelationshipsApi#listChildGuardianRelationships
     */
    @Override
    public PageOfChildGuardianRelationships listChildGuardianRelationships(
        @Parameter(name = "page", description = "", in = ParameterIn.QUERY) @RequestParam(value = "page", required = false) Integer page,
        @Parameter(name = "size", description = "", in = ParameterIn.QUERY) @RequestParam(value = "size", required = false) Integer size,
        @Parameter(name = "sort", description = "e.g. created_at,desc", in = ParameterIn.QUERY) @RequestParam(value = "sort", required = false) String sort
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

}
