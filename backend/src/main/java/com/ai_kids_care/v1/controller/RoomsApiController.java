package com.ai_kids_care.v1.controller;

import com.ai_kids_care.v1.api.RoomsApi;
import com.ai_kids_care.v1.dto.PageOfRooms;
import com.ai_kids_care.v1.entity.Room;
import com.ai_kids_care.v1.dto.RoomsCreateRequest;
import com.ai_kids_care.v1.dto.RoomsUpdateRequest;

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
public class RoomsApiController implements RoomsApi {

    /**
     * POST /v1/rooms : Create rooms
     *
     * @param roomsCreateRequest  (required)
     * @return Created (status code 201)
     *         or Bad Request (status code 400)
     *         or Conflict (status code 409)
     *         or Internal Server Error (status code 500)
     * @see RoomsApi#createRooms
     */
    @Override
    public Room createRooms(
        @Parameter(name = "RoomsCreateRequest", description = "", required = true) @RequestBody RoomsCreateRequest roomsCreateRequest
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

    /**
     * DELETE /v1/rooms/{room_id} : Delete rooms by room_id
     *
     * @param roomId  (required)
     * @return No Content (status code 204)
     *         or Not Found (status code 404)
     *         or Internal Server Error (status code 500)
     * @see RoomsApi#deleteRooms
     */
    @Override
    public void deleteRooms(
        @Parameter(name = "room_id", description = "", required = true, in = ParameterIn.PATH) @PathVariable("room_id") Long roomId
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

    /**
     * GET /v1/rooms/{room_id} : Get rooms by room_id
     *
     * @param roomId  (required)
     * @return OK (status code 200)
     *         or Not Found (status code 404)
     *         or Internal Server Error (status code 500)
     * @see RoomsApi#getRooms
     */
    @Override
    public Room getRooms(
        @Parameter(name = "room_id", description = "", required = true, in = ParameterIn.PATH) @PathVariable("room_id") Long roomId
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

    /**
     * GET /v1/rooms : List rooms
     *
     * @param page  (optional)
     * @param size  (optional)
     * @param sort e.g. created_at,desc (optional)
     * @return OK (status code 200)
     *         or Bad Request (status code 400)
     *         or Internal Server Error (status code 500)
     * @see RoomsApi#listRooms
     */
    @Override
    public PageOfRooms listRooms(
        @Parameter(name = "page", description = "", in = ParameterIn.QUERY) @RequestParam(value = "page", required = false) Integer page,
        @Parameter(name = "size", description = "", in = ParameterIn.QUERY) @RequestParam(value = "size", required = false) Integer size,
        @Parameter(name = "sort", description = "e.g. created_at,desc", in = ParameterIn.QUERY) @RequestParam(value = "sort", required = false) String sort
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

    /**
     * PUT /v1/rooms/{room_id} : Update rooms by room_id
     *
     * @param roomId  (required)
     * @param roomsUpdateRequest  (required)
     * @return OK (status code 200)
     *         or Bad Request (status code 400)
     *         or Not Found (status code 404)
     *         or Conflict (status code 409)
     *         or Internal Server Error (status code 500)
     * @see RoomsApi#updateRooms
     */
    @Override
    public Room updateRooms(
        @Parameter(name = "room_id", description = "", required = true, in = ParameterIn.PATH) @PathVariable("room_id") Long roomId,
        @Parameter(name = "RoomsUpdateRequest", description = "", required = true) @RequestBody RoomsUpdateRequest roomsUpdateRequest
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

}
