package com.ai_kids_care.v1.controller;

import com.ai_kids_care.v1.api.TeachersApi;
import com.ai_kids_care.v1.dto.PageOfTeachers;
import com.ai_kids_care.v1.entity.Teacher;
import com.ai_kids_care.v1.dto.TeachersCreateRequest;
import com.ai_kids_care.v1.dto.TeachersUpdateRequest;

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
public class TeachersApiController implements TeachersApi {

    /**
     * POST /v1/teachers : Create teachers
     *
     * @param teachersCreateRequest  (required)
     * @return Created (status code 201)
     *         or Bad Request (status code 400)
     *         or Conflict (status code 409)
     *         or Internal Server Error (status code 500)
     * @see TeachersApi#createTeachers
     */
    @Override
    public Teacher createTeachers(
        @Parameter(name = "TeachersCreateRequest", description = "", required = true) @RequestBody TeachersCreateRequest teachersCreateRequest
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

    /**
     * DELETE /v1/teachers/{teacher_id} : Delete teachers by teacher_id
     *
     * @param teacherId  (required)
     * @return No Content (status code 204)
     *         or Not Found (status code 404)
     *         or Internal Server Error (status code 500)
     * @see TeachersApi#deleteTeachers
     */
    @Override
    public void deleteTeachers(
        @Parameter(name = "teacher_id", description = "", required = true, in = ParameterIn.PATH) @PathVariable("teacher_id") Long teacherId
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

    /**
     * GET /v1/teachers/{teacher_id} : Get teachers by teacher_id
     *
     * @param teacherId  (required)
     * @return OK (status code 200)
     *         or Not Found (status code 404)
     *         or Internal Server Error (status code 500)
     * @see TeachersApi#getTeachers
     */
    @Override
    public Teacher getTeachers(
        @Parameter(name = "teacher_id", description = "", required = true, in = ParameterIn.PATH) @PathVariable("teacher_id") Long teacherId
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

    /**
     * GET /v1/teachers : List teachers
     *
     * @param page  (optional)
     * @param size  (optional)
     * @param sort e.g. created_at,desc (optional)
     * @return OK (status code 200)
     *         or Bad Request (status code 400)
     *         or Internal Server Error (status code 500)
     * @see TeachersApi#listTeachers
     */
    @Override
    public PageOfTeachers listTeachers(
        @Parameter(name = "page", description = "", in = ParameterIn.QUERY) @RequestParam(value = "page", required = false) Integer page,
        @Parameter(name = "size", description = "", in = ParameterIn.QUERY) @RequestParam(value = "size", required = false) Integer size,
        @Parameter(name = "sort", description = "e.g. created_at,desc", in = ParameterIn.QUERY) @RequestParam(value = "sort", required = false) String sort
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

    /**
     * PUT /v1/teachers/{teacher_id} : Update teachers by teacher_id
     *
     * @param teacherId  (required)
     * @param teachersUpdateRequest  (required)
     * @return OK (status code 200)
     *         or Bad Request (status code 400)
     *         or Not Found (status code 404)
     *         or Conflict (status code 409)
     *         or Internal Server Error (status code 500)
     * @see TeachersApi#updateTeachers
     */
    @Override
    public Teacher updateTeachers(
        @Parameter(name = "teacher_id", description = "", required = true, in = ParameterIn.PATH) @PathVariable("teacher_id") Long teacherId,
        @Parameter(name = "TeachersUpdateRequest", description = "", required = true) @RequestBody TeachersUpdateRequest teachersUpdateRequest
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

}
