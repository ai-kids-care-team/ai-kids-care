package com.ai_kids_care.v1.controller;

import com.ai_kids_care.v1.api.SuperadminsApi;
import com.ai_kids_care.v1.dto.PageOfSuperadmins;
import com.ai_kids_care.v1.entity.Superadmin;
import com.ai_kids_care.v1.dto.SuperadminsCreateRequest;
import com.ai_kids_care.v1.dto.SuperadminsUpdateRequest;

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
public class SuperadminsApiController implements SuperadminsApi {

    /**
     * POST /v1/superadmins : Create superadmins
     *
     * @param superadminsCreateRequest  (required)
     * @return Created (status code 201)
     *         or Bad Request (status code 400)
     *         or Conflict (status code 409)
     *         or Internal Server Error (status code 500)
     * @see SuperadminsApi#createSuperadmins
     */
    @Override
    public Superadmin createSuperadmins(
        @Parameter(name = "SuperadminsCreateRequest", description = "", required = true) @RequestBody SuperadminsCreateRequest superadminsCreateRequest
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

    /**
     * DELETE /v1/superadmins/{superadmin_id} : Delete superadmins by superadmin_id
     *
     * @param superadminId  (required)
     * @return No Content (status code 204)
     *         or Not Found (status code 404)
     *         or Internal Server Error (status code 500)
     * @see SuperadminsApi#deleteSuperadmins
     */
    @Override
    public void deleteSuperadmins(
        @Parameter(name = "superadmin_id", description = "", required = true, in = ParameterIn.PATH) @PathVariable("superadmin_id") Long superadminId
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

    /**
     * GET /v1/superadmins/{superadmin_id} : Get superadmins by superadmin_id
     *
     * @param superadminId  (required)
     * @return OK (status code 200)
     *         or Not Found (status code 404)
     *         or Internal Server Error (status code 500)
     * @see SuperadminsApi#getSuperadmins
     */
    @Override
    public Superadmin getSuperadmins(
        @Parameter(name = "superadmin_id", description = "", required = true, in = ParameterIn.PATH) @PathVariable("superadmin_id") Long superadminId
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

    /**
     * GET /v1/superadmins : List superadmins
     *
     * @param page  (optional)
     * @param size  (optional)
     * @param sort e.g. created_at,desc (optional)
     * @return OK (status code 200)
     *         or Bad Request (status code 400)
     *         or Internal Server Error (status code 500)
     * @see SuperadminsApi#listSuperadmins
     */
    @Override
    public PageOfSuperadmins listSuperadmins(
        @Parameter(name = "page", description = "", in = ParameterIn.QUERY) @RequestParam(value = "page", required = false) Integer page,
        @Parameter(name = "size", description = "", in = ParameterIn.QUERY) @RequestParam(value = "size", required = false) Integer size,
        @Parameter(name = "sort", description = "e.g. created_at,desc", in = ParameterIn.QUERY) @RequestParam(value = "sort", required = false) String sort
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

    /**
     * PUT /v1/superadmins/{superadmin_id} : Update superadmins by superadmin_id
     *
     * @param superadminId  (required)
     * @param superadminsUpdateRequest  (required)
     * @return OK (status code 200)
     *         or Bad Request (status code 400)
     *         or Not Found (status code 404)
     *         or Conflict (status code 409)
     *         or Internal Server Error (status code 500)
     * @see SuperadminsApi#updateSuperadmins
     */
    @Override
    public Superadmin updateSuperadmins(
        @Parameter(name = "superadmin_id", description = "", required = true, in = ParameterIn.PATH) @PathVariable("superadmin_id") Long superadminId,
        @Parameter(name = "SuperadminsUpdateRequest", description = "", required = true) @RequestBody SuperadminsUpdateRequest superadminsUpdateRequest
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

}
