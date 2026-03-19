package com.ai_kids_care.v1.controller;

import com.ai_kids_care.v1.api.GuardiansApi;
import com.ai_kids_care.v1.entity.Guardian;
import com.ai_kids_care.v1.dto.GuardiansCreateRequest;
import com.ai_kids_care.v1.dto.GuardiansUpdateRequest;
import com.ai_kids_care.v1.dto.PageOfGuardians;

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
public class GuardiansApiController implements GuardiansApi {

    /**
     * POST /v1/guardians : Create guardians
     *
     * @param guardiansCreateRequest  (required)
     * @return Created (status code 201)
     *         or Bad Request (status code 400)
     *         or Conflict (status code 409)
     *         or Internal Server Error (status code 500)
     * @see GuardiansApi#createGuardians
     */
    @Override
    public Guardian createGuardians(
        @Parameter(name = "GuardiansCreateRequest", description = "", required = true) @RequestBody GuardiansCreateRequest guardiansCreateRequest
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

    /**
     * DELETE /v1/guardians/{guardian_id} : Delete guardians by guardian_id
     *
     * @param guardianId  (required)
     * @return No Content (status code 204)
     *         or Not Found (status code 404)
     *         or Internal Server Error (status code 500)
     * @see GuardiansApi#deleteGuardians
     */
    @Override
    public void deleteGuardians(
        @Parameter(name = "guardian_id", description = "", required = true, in = ParameterIn.PATH) @PathVariable("guardian_id") Long guardianId
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

    /**
     * GET /v1/guardians/{guardian_id} : Get guardians by guardian_id
     *
     * @param guardianId  (required)
     * @return OK (status code 200)
     *         or Not Found (status code 404)
     *         or Internal Server Error (status code 500)
     * @see GuardiansApi#getGuardians
     */
    @Override
    public Guardian getGuardians(
        @Parameter(name = "guardian_id", description = "", required = true, in = ParameterIn.PATH) @PathVariable("guardian_id") Long guardianId
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

    /**
     * GET /v1/guardians : List guardians
     *
     * @param page  (optional)
     * @param size  (optional)
     * @param sort e.g. created_at,desc (optional)
     * @return OK (status code 200)
     *         or Bad Request (status code 400)
     *         or Internal Server Error (status code 500)
     * @see GuardiansApi#listGuardians
     */
    @Override
    public PageOfGuardians listGuardians(
        @Parameter(name = "page", description = "", in = ParameterIn.QUERY) @RequestParam(value = "page", required = false) Integer page,
        @Parameter(name = "size", description = "", in = ParameterIn.QUERY) @RequestParam(value = "size", required = false) Integer size,
        @Parameter(name = "sort", description = "e.g. created_at,desc", in = ParameterIn.QUERY) @RequestParam(value = "sort", required = false) String sort
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

    /**
     * PUT /v1/guardians/{guardian_id} : Update guardians by guardian_id
     *
     * @param guardianId  (required)
     * @param guardiansUpdateRequest  (required)
     * @return OK (status code 200)
     *         or Bad Request (status code 400)
     *         or Not Found (status code 404)
     *         or Conflict (status code 409)
     *         or Internal Server Error (status code 500)
     * @see GuardiansApi#updateGuardians
     */
    @Override
    public Guardian updateGuardians(
        @Parameter(name = "guardian_id", description = "", required = true, in = ParameterIn.PATH) @PathVariable("guardian_id") Long guardianId,
        @Parameter(name = "GuardiansUpdateRequest", description = "", required = true) @RequestBody GuardiansUpdateRequest guardiansUpdateRequest
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

}
