package com.ai_kids_care.v1.controller;

import com.ai_kids_care.v1.api.ClassTeacherAssignmentsApi;
import com.ai_kids_care.v1.entity.ClassTeacherAssignments;
import com.ai_kids_care.v1.dto.ClassTeacherAssignmentsCreateRequest;
import com.ai_kids_care.v1.dto.ClassTeacherAssignmentsUpdateRequest;
import com.ai_kids_care.v1.dto.PageOfClassTeacherAssignments;

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
public class ClassTeacherAssignmentsApiController implements ClassTeacherAssignmentsApi {

    /**
     * POST /v1/class_teacher_assignments : Create class_teacher_assignments
     *
     * @param classTeacherAssignmentsCreateRequest  (required)
     * @return Created (status code 201)
     *         or Bad Request (status code 400)
     *         or Conflict (status code 409)
     *         or Internal Server Error (status code 500)
     * @see ClassTeacherAssignmentsApi#createClassTeacherAssignments
     */
    @Override
    public ClassTeacherAssignments createClassTeacherAssignments(
        @Parameter(name = "ClassTeacherAssignmentsCreateRequest", description = "", required = true) @RequestBody ClassTeacherAssignmentsCreateRequest classTeacherAssignmentsCreateRequest
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

    /**
     * DELETE /v1/class_teacher_assignments/{assignment_id} : Delete class_teacher_assignments by assignment_id
     *
     * @param assignmentId  (required)
     * @return No Content (status code 204)
     *         or Not Found (status code 404)
     *         or Internal Server Error (status code 500)
     * @see ClassTeacherAssignmentsApi#deleteClassTeacherAssignments
     */
    @Override
    public void deleteClassTeacherAssignments(
        @Parameter(name = "assignment_id", description = "", required = true, in = ParameterIn.PATH) @PathVariable("assignment_id") Long assignmentId
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

    /**
     * GET /v1/class_teacher_assignments/{assignment_id} : Get class_teacher_assignments by assignment_id
     *
     * @param assignmentId  (required)
     * @return OK (status code 200)
     *         or Not Found (status code 404)
     *         or Internal Server Error (status code 500)
     * @see ClassTeacherAssignmentsApi#getClassTeacherAssignments
     */
    @Override
    public ClassTeacherAssignments getClassTeacherAssignments(
        @Parameter(name = "assignment_id", description = "", required = true, in = ParameterIn.PATH) @PathVariable("assignment_id") Long assignmentId
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

    /**
     * GET /v1/class_teacher_assignments : List class_teacher_assignments
     *
     * @param page  (optional)
     * @param size  (optional)
     * @param sort e.g. created_at,desc (optional)
     * @return OK (status code 200)
     *         or Bad Request (status code 400)
     *         or Internal Server Error (status code 500)
     * @see ClassTeacherAssignmentsApi#listClassTeacherAssignments
     */
    @Override
    public PageOfClassTeacherAssignments listClassTeacherAssignments(
        @Parameter(name = "page", description = "", in = ParameterIn.QUERY) @RequestParam(value = "page", required = false) Integer page,
        @Parameter(name = "size", description = "", in = ParameterIn.QUERY) @RequestParam(value = "size", required = false) Integer size,
        @Parameter(name = "sort", description = "e.g. created_at,desc", in = ParameterIn.QUERY) @RequestParam(value = "sort", required = false) String sort
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

    /**
     * PUT /v1/class_teacher_assignments/{assignment_id} : Update class_teacher_assignments by assignment_id
     *
     * @param assignmentId  (required)
     * @param classTeacherAssignmentsUpdateRequest  (required)
     * @return OK (status code 200)
     *         or Bad Request (status code 400)
     *         or Not Found (status code 404)
     *         or Conflict (status code 409)
     *         or Internal Server Error (status code 500)
     * @see ClassTeacherAssignmentsApi#updateClassTeacherAssignments
     */
    @Override
    public ClassTeacherAssignments updateClassTeacherAssignments(
        @Parameter(name = "assignment_id", description = "", required = true, in = ParameterIn.PATH) @PathVariable("assignment_id") Long assignmentId,
        @Parameter(name = "ClassTeacherAssignmentsUpdateRequest", description = "", required = true) @RequestBody ClassTeacherAssignmentsUpdateRequest classTeacherAssignmentsUpdateRequest
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

}
