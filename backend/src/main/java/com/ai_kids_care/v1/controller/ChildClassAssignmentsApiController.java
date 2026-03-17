package com.ai_kids_care.v1.controller;

import com.ai_kids_care.v1.api.ChildClassAssignmentsApi;
import com.ai_kids_care.v1.entity.ChildClassAssignments;
import com.ai_kids_care.v1.dto.ChildClassAssignmentsCreateRequest;
import com.ai_kids_care.v1.dto.ChildClassAssignmentsUpdateRequest;
import com.ai_kids_care.v1.entity.PageOfChildClassAssignments;

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
public class ChildClassAssignmentsApiController implements ChildClassAssignmentsApi {

    /**
     * POST /v1/child_class_assignments : Create child_class_assignments
     *
     * @param childClassAssignmentsCreateRequest  (required)
     * @return Created (status code 201)
     *         or Bad Request (status code 400)
     *         or Conflict (status code 409)
     *         or Internal Server Error (status code 500)
     * @see ChildClassAssignmentsApi#createChildClassAssignments
     */
    @Override
    public ChildClassAssignments createChildClassAssignments(
        @Parameter(name = "ChildClassAssignmentsCreateRequest", description = "", required = true) @RequestBody ChildClassAssignmentsCreateRequest childClassAssignmentsCreateRequest
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

    /**
     * DELETE /v1/child_class_assignments/{assignment_id} : Delete child_class_assignments by assignment_id
     *
     * @param assignmentId  (required)
     * @return No Content (status code 204)
     *         or Not Found (status code 404)
     *         or Internal Server Error (status code 500)
     * @see ChildClassAssignmentsApi#deleteChildClassAssignments
     */
    @Override
    public void deleteChildClassAssignments(
        @Parameter(name = "assignment_id", description = "", required = true, in = ParameterIn.PATH) @PathVariable("assignment_id") Long assignmentId
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

    /**
     * GET /v1/child_class_assignments/{assignment_id} : Get child_class_assignments by assignment_id
     *
     * @param assignmentId  (required)
     * @return OK (status code 200)
     *         or Not Found (status code 404)
     *         or Internal Server Error (status code 500)
     * @see ChildClassAssignmentsApi#getChildClassAssignments
     */
    @Override
    public ChildClassAssignments getChildClassAssignments(
        @Parameter(name = "assignment_id", description = "", required = true, in = ParameterIn.PATH) @PathVariable("assignment_id") Long assignmentId
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

    /**
     * GET /v1/child_class_assignments : List child_class_assignments
     *
     * @param page  (optional)
     * @param size  (optional)
     * @param sort e.g. created_at,desc (optional)
     * @return OK (status code 200)
     *         or Bad Request (status code 400)
     *         or Internal Server Error (status code 500)
     * @see ChildClassAssignmentsApi#listChildClassAssignments
     */
    @Override
    public PageOfChildClassAssignments listChildClassAssignments(
        @Parameter(name = "page", description = "", in = ParameterIn.QUERY) @RequestParam(value = "page", required = false) Integer page,
        @Parameter(name = "size", description = "", in = ParameterIn.QUERY) @RequestParam(value = "size", required = false) Integer size,
        @Parameter(name = "sort", description = "e.g. created_at,desc", in = ParameterIn.QUERY) @RequestParam(value = "sort", required = false) String sort
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

    /**
     * PUT /v1/child_class_assignments/{assignment_id} : Update child_class_assignments by assignment_id
     *
     * @param assignmentId  (required)
     * @param childClassAssignmentsUpdateRequest  (required)
     * @return OK (status code 200)
     *         or Bad Request (status code 400)
     *         or Not Found (status code 404)
     *         or Conflict (status code 409)
     *         or Internal Server Error (status code 500)
     * @see ChildClassAssignmentsApi#updateChildClassAssignments
     */
    @Override
    public ChildClassAssignments updateChildClassAssignments(
        @Parameter(name = "assignment_id", description = "", required = true, in = ParameterIn.PATH) @PathVariable("assignment_id") Long assignmentId,
        @Parameter(name = "ChildClassAssignmentsUpdateRequest", description = "", required = true) @RequestBody ChildClassAssignmentsUpdateRequest childClassAssignmentsUpdateRequest
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

}
