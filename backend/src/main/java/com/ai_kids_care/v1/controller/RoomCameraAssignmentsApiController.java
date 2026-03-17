package com.ai_kids_care.v1.controller;

import com.ai_kids_care.v1.api.RoomCameraAssignmentsApi;
import com.ai_kids_care.v1.entity.PageOfRoomCameraAssignments;
import com.ai_kids_care.v1.entity.RoomCameraAssignments;
import com.ai_kids_care.v1.dto.RoomCameraAssignmentsCreateRequest;
import com.ai_kids_care.v1.dto.RoomCameraAssignmentsUpdateRequest;

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
public class RoomCameraAssignmentsApiController implements RoomCameraAssignmentsApi {

    /**
     * POST /v1/room_camera_assignments : Create room_camera_assignments
     *
     * @param roomCameraAssignmentsCreateRequest  (required)
     * @return Created (status code 201)
     *         or Bad Request (status code 400)
     *         or Conflict (status code 409)
     *         or Internal Server Error (status code 500)
     * @see RoomCameraAssignmentsApi#createRoomCameraAssignments
     */
    @Override
    public RoomCameraAssignments createRoomCameraAssignments(
        @Parameter(name = "RoomCameraAssignmentsCreateRequest", description = "", required = true) @RequestBody RoomCameraAssignmentsCreateRequest roomCameraAssignmentsCreateRequest
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

    /**
     * DELETE /v1/room_camera_assignments/{assignment_id} : Delete room_camera_assignments by assignment_id
     *
     * @param assignmentId  (required)
     * @return No Content (status code 204)
     *         or Not Found (status code 404)
     *         or Internal Server Error (status code 500)
     * @see RoomCameraAssignmentsApi#deleteRoomCameraAssignments
     */
    @Override
    public void deleteRoomCameraAssignments(
        @Parameter(name = "assignment_id", description = "", required = true, in = ParameterIn.PATH) @PathVariable("assignment_id") Long assignmentId
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

    /**
     * GET /v1/room_camera_assignments/{assignment_id} : Get room_camera_assignments by assignment_id
     *
     * @param assignmentId  (required)
     * @return OK (status code 200)
     *         or Not Found (status code 404)
     *         or Internal Server Error (status code 500)
     * @see RoomCameraAssignmentsApi#getRoomCameraAssignments
     */
    @Override
    public RoomCameraAssignments getRoomCameraAssignments(
        @Parameter(name = "assignment_id", description = "", required = true, in = ParameterIn.PATH) @PathVariable("assignment_id") Long assignmentId
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

    /**
     * GET /v1/room_camera_assignments : List room_camera_assignments
     *
     * @param page  (optional)
     * @param size  (optional)
     * @param sort e.g. created_at,desc (optional)
     * @return OK (status code 200)
     *         or Bad Request (status code 400)
     *         or Internal Server Error (status code 500)
     * @see RoomCameraAssignmentsApi#listRoomCameraAssignments
     */
    @Override
    public PageOfRoomCameraAssignments listRoomCameraAssignments(
        @Parameter(name = "page", description = "", in = ParameterIn.QUERY) @RequestParam(value = "page", required = false) Integer page,
        @Parameter(name = "size", description = "", in = ParameterIn.QUERY) @RequestParam(value = "size", required = false) Integer size,
        @Parameter(name = "sort", description = "e.g. created_at,desc", in = ParameterIn.QUERY) @RequestParam(value = "sort", required = false) String sort
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

    /**
     * PUT /v1/room_camera_assignments/{assignment_id} : Update room_camera_assignments by assignment_id
     *
     * @param assignmentId  (required)
     * @param roomCameraAssignmentsUpdateRequest  (required)
     * @return OK (status code 200)
     *         or Bad Request (status code 400)
     *         or Not Found (status code 404)
     *         or Conflict (status code 409)
     *         or Internal Server Error (status code 500)
     * @see RoomCameraAssignmentsApi#updateRoomCameraAssignments
     */
    @Override
    public RoomCameraAssignments updateRoomCameraAssignments(
        @Parameter(name = "assignment_id", description = "", required = true, in = ParameterIn.PATH) @PathVariable("assignment_id") Long assignmentId,
        @Parameter(name = "RoomCameraAssignmentsUpdateRequest", description = "", required = true) @RequestBody RoomCameraAssignmentsUpdateRequest roomCameraAssignmentsUpdateRequest
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

}
