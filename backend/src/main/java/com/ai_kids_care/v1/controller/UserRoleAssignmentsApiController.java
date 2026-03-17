package com.ai_kids_care.v1.controller;

import com.ai_kids_care.v1.api.UserRoleAssignmentsApi;
import com.ai_kids_care.v1.dto.PageOfUserRoleAssignments;
import com.ai_kids_care.v1.entity.UserRoleAssignment;
import com.ai_kids_care.v1.dto.UserRoleAssignmentsCreateRequest;
import com.ai_kids_care.v1.dto.UserRoleAssignmentsUpdateRequest;

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
public class UserRoleAssignmentsApiController implements UserRoleAssignmentsApi {

    /**
     * POST /v1/user_role_assignments : Create user_role_assignments
     *
     * @param userRoleAssignmentsCreateRequest  (required)
     * @return Created (status code 201)
     *         or Bad Request (status code 400)
     *         or Conflict (status code 409)
     *         or Internal Server Error (status code 500)
     * @see UserRoleAssignmentsApi#createUserRoleAssignments
     */
    @Override
    public UserRoleAssignment createUserRoleAssignments(
        @Parameter(name = "UserRoleAssignmentsCreateRequest", description = "", required = true) @RequestBody UserRoleAssignmentsCreateRequest userRoleAssignmentsCreateRequest
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

    /**
     * DELETE /v1/user_role_assignments/{role_assignment_id} : Delete user_role_assignments by role_assignment_id
     *
     * @param roleAssignmentId  (required)
     * @return No Content (status code 204)
     *         or Not Found (status code 404)
     *         or Internal Server Error (status code 500)
     * @see UserRoleAssignmentsApi#deleteUserRoleAssignments
     */
    @Override
    public void deleteUserRoleAssignments(
        @Parameter(name = "role_assignment_id", description = "", required = true, in = ParameterIn.PATH) @PathVariable("role_assignment_id") Long roleAssignmentId
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

    /**
     * GET /v1/user_role_assignments/{role_assignment_id} : Get user_role_assignments by role_assignment_id
     *
     * @param roleAssignmentId  (required)
     * @return OK (status code 200)
     *         or Not Found (status code 404)
     *         or Internal Server Error (status code 500)
     * @see UserRoleAssignmentsApi#getUserRoleAssignments
     */
    @Override
    public UserRoleAssignment getUserRoleAssignments(
        @Parameter(name = "role_assignment_id", description = "", required = true, in = ParameterIn.PATH) @PathVariable("role_assignment_id") Long roleAssignmentId
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

    /**
     * GET /v1/user_role_assignments : List user_role_assignments
     *
     * @param page  (optional)
     * @param size  (optional)
     * @param sort e.g. created_at,desc (optional)
     * @return OK (status code 200)
     *         or Bad Request (status code 400)
     *         or Internal Server Error (status code 500)
     * @see UserRoleAssignmentsApi#listUserRoleAssignments
     */
    @Override
    public PageOfUserRoleAssignments listUserRoleAssignments(
        @Parameter(name = "page", description = "", in = ParameterIn.QUERY) @RequestParam(value = "page", required = false) Integer page,
        @Parameter(name = "size", description = "", in = ParameterIn.QUERY) @RequestParam(value = "size", required = false) Integer size,
        @Parameter(name = "sort", description = "e.g. created_at,desc", in = ParameterIn.QUERY) @RequestParam(value = "sort", required = false) String sort
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

    /**
     * PUT /v1/user_role_assignments/{role_assignment_id} : Update user_role_assignments by role_assignment_id
     *
     * @param roleAssignmentId  (required)
     * @param userRoleAssignmentsUpdateRequest  (required)
     * @return OK (status code 200)
     *         or Bad Request (status code 400)
     *         or Not Found (status code 404)
     *         or Conflict (status code 409)
     *         or Internal Server Error (status code 500)
     * @see UserRoleAssignmentsApi#updateUserRoleAssignments
     */
    @Override
    public UserRoleAssignment updateUserRoleAssignments(
        @Parameter(name = "role_assignment_id", description = "", required = true, in = ParameterIn.PATH) @PathVariable("role_assignment_id") Long roleAssignmentId,
        @Parameter(name = "UserRoleAssignmentsUpdateRequest", description = "", required = true) @RequestBody UserRoleAssignmentsUpdateRequest userRoleAssignmentsUpdateRequest
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

}
