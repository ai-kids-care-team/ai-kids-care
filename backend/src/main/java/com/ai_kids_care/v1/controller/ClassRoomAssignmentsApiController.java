package com.ai_kids_care.v1.controller;

import com.ai_kids_care.v1.api.ClassRoomAssignmentsApi;
import com.ai_kids_care.v1.entity.ClassRoomAssignment;
import com.ai_kids_care.v1.dto.ClassRoomAssignmentsCreateRequest;
import com.ai_kids_care.v1.dto.ClassRoomAssignmentsUpdateRequest;
import com.ai_kids_care.v1.dto.PageOfClassRoomAssignments;

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
public class ClassRoomAssignmentsApiController implements ClassRoomAssignmentsApi {

    /**
     * POST /v1/class_room_assignments : Create class_room_assignments
     *
     * @param classRoomAssignmentsCreateRequest  (required)
     * @return Created (status code 201)
     *         or Bad Request (status code 400)
     *         or Conflict (status code 409)
     *         or Internal Server Error (status code 500)
     * @see ClassRoomAssignmentsApi#createClassRoomAssignments
     */
    @Override
    public ClassRoomAssignment createClassRoomAssignments(
        @Parameter(name = "ClassRoomAssignmentsCreateRequest", description = "", required = true) @RequestBody ClassRoomAssignmentsCreateRequest classRoomAssignmentsCreateRequest
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

    /**
     * DELETE /v1/class_room_assignments/{assignment_id} : Delete class_room_assignments by assignment_id
     *
     * @param assignmentId  (required)
     * @return No Content (status code 204)
     *         or Not Found (status code 404)
     *         or Internal Server Error (status code 500)
     * @see ClassRoomAssignmentsApi#deleteClassRoomAssignments
     */
    @Override
    public void deleteClassRoomAssignments(
        @Parameter(name = "assignment_id", description = "", required = true, in = ParameterIn.PATH) @PathVariable("assignment_id") Long assignmentId
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

    /**
     * GET /v1/class_room_assignments/{assignment_id} : Get class_room_assignments by assignment_id
     *
     * @param assignmentId  (required)
     * @return OK (status code 200)
     *         or Not Found (status code 404)
     *         or Internal Server Error (status code 500)
     * @see ClassRoomAssignmentsApi#getClassRoomAssignments
     */
    @Override
    public ClassRoomAssignment getClassRoomAssignments(
        @Parameter(name = "assignment_id", description = "", required = true, in = ParameterIn.PATH) @PathVariable("assignment_id") Long assignmentId
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

    /**
     * GET /v1/class_room_assignments : List class_room_assignments
     *
     * @param page  (optional)
     * @param size  (optional)
     * @param sort e.g. created_at,desc (optional)
     * @return OK (status code 200)
     *         or Bad Request (status code 400)
     *         or Internal Server Error (status code 500)
     * @see ClassRoomAssignmentsApi#listClassRoomAssignments
     */
    @Override
    public PageOfClassRoomAssignments listClassRoomAssignments(
        @Parameter(name = "page", description = "", in = ParameterIn.QUERY) @RequestParam(value = "page", required = false) Integer page,
        @Parameter(name = "size", description = "", in = ParameterIn.QUERY) @RequestParam(value = "size", required = false) Integer size,
        @Parameter(name = "sort", description = "e.g. created_at,desc", in = ParameterIn.QUERY) @RequestParam(value = "sort", required = false) String sort
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

    /**
     * PUT /v1/class_room_assignments/{assignment_id} : Update class_room_assignments by assignment_id
     *
     * @param assignmentId  (required)
     * @param classRoomAssignmentsUpdateRequest  (required)
     * @return OK (status code 200)
     *         or Bad Request (status code 400)
     *         or Not Found (status code 404)
     *         or Conflict (status code 409)
     *         or Internal Server Error (status code 500)
     * @see ClassRoomAssignmentsApi#updateClassRoomAssignments
     */
    @Override
    public ClassRoomAssignment updateClassRoomAssignments(
        @Parameter(name = "assignment_id", description = "", required = true, in = ParameterIn.PATH) @PathVariable("assignment_id") Long assignmentId,
        @Parameter(name = "ClassRoomAssignmentsUpdateRequest", description = "", required = true) @RequestBody ClassRoomAssignmentsUpdateRequest classRoomAssignmentsUpdateRequest
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

}
