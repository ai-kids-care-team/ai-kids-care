package com.ai_kids_care.v1.controller;

import com.ai_kids_care.v1.api.ClassesApi;
import com.ai_kids_care.v1.entity.Class;
import com.ai_kids_care.v1.dto.ClassesCreateRequest;
import com.ai_kids_care.v1.dto.ClassesUpdateRequest;
import com.ai_kids_care.v1.dto.PageOfClasses;

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
public class ClassesApiController implements ClassesApi {

    /**
     * POST /v1/classes : Create classes
     *
     * @param classesCreateRequest  (required)
     * @return Created (status code 201)
     *         or Bad Request (status code 400)
     *         or Conflict (status code 409)
     *         or Internal Server Error (status code 500)
     * @see ClassesApi#createClasses
     */
    @Override
    public Class createClasses(
        @Parameter(name = "ClassesCreateRequest", description = "", required = true) @RequestBody ClassesCreateRequest classesCreateRequest
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

    /**
     * DELETE /v1/classes/{class_id} : Delete classes by class_id
     *
     * @param classId  (required)
     * @return No Content (status code 204)
     *         or Not Found (status code 404)
     *         or Internal Server Error (status code 500)
     * @see ClassesApi#deleteClasses
     */
    @Override
    public void deleteClasses(
        @Parameter(name = "class_id", description = "", required = true, in = ParameterIn.PATH) @PathVariable("class_id") Long classId
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

    /**
     * GET /v1/classes/{class_id} : Get classes by class_id
     *
     * @param classId  (required)
     * @return OK (status code 200)
     *         or Not Found (status code 404)
     *         or Internal Server Error (status code 500)
     * @see ClassesApi#getClasses
     */
    @Override
    public Class getClasses(
        @Parameter(name = "class_id", description = "", required = true, in = ParameterIn.PATH) @PathVariable("class_id") Long classId
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

    /**
     * GET /v1/classes : List classes
     *
     * @param page  (optional)
     * @param size  (optional)
     * @param sort e.g. created_at,desc (optional)
     * @return OK (status code 200)
     *         or Bad Request (status code 400)
     *         or Internal Server Error (status code 500)
     * @see ClassesApi#listClasses
     */
    @Override
    public PageOfClasses listClasses(
        @Parameter(name = "page", description = "", in = ParameterIn.QUERY) @RequestParam(value = "page", required = false) Integer page,
        @Parameter(name = "size", description = "", in = ParameterIn.QUERY) @RequestParam(value = "size", required = false) Integer size,
        @Parameter(name = "sort", description = "e.g. created_at,desc", in = ParameterIn.QUERY) @RequestParam(value = "sort", required = false) String sort
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

    /**
     * PUT /v1/classes/{class_id} : Update classes by class_id
     *
     * @param classId  (required)
     * @param classesUpdateRequest  (required)
     * @return OK (status code 200)
     *         or Bad Request (status code 400)
     *         or Not Found (status code 404)
     *         or Conflict (status code 409)
     *         or Internal Server Error (status code 500)
     * @see ClassesApi#updateClasses
     */
    @Override
    public Class updateClasses(
        @Parameter(name = "class_id", description = "", required = true, in = ParameterIn.PATH) @PathVariable("class_id") Long classId,
        @Parameter(name = "ClassesUpdateRequest", description = "", required = true) @RequestBody ClassesUpdateRequest classesUpdateRequest
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

}
