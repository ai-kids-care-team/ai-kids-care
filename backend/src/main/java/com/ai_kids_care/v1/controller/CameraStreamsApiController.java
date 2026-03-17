package com.ai_kids_care.v1.controller;

import com.ai_kids_care.v1.api.CameraStreamsApi;
import com.ai_kids_care.v1.entity.CameraStreams;
import com.ai_kids_care.v1.dto.CameraStreamsCreateRequest;
import com.ai_kids_care.v1.dto.CameraStreamsUpdateRequest;
import com.ai_kids_care.v1.dto.PageOfCameraStreams;

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
public class CameraStreamsApiController implements CameraStreamsApi {

    /**
     * POST /v1/camera_streams : Create camera_streams
     *
     * @param cameraStreamsCreateRequest  (required)
     * @return Created (status code 201)
     *         or Bad Request (status code 400)
     *         or Conflict (status code 409)
     *         or Internal Server Error (status code 500)
     * @see CameraStreamsApi#createCameraStreams
     */
    @Override
    public CameraStreams createCameraStreams(
        @Parameter(name = "CameraStreamsCreateRequest", description = "", required = true) @RequestBody CameraStreamsCreateRequest cameraStreamsCreateRequest
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

    /**
     * DELETE /v1/camera_streams/{stream_id} : Delete camera_streams by stream_id
     *
     * @param streamId  (required)
     * @return No Content (status code 204)
     *         or Not Found (status code 404)
     *         or Internal Server Error (status code 500)
     * @see CameraStreamsApi#deleteCameraStreams
     */
    @Override
    public void deleteCameraStreams(
        @Parameter(name = "stream_id", description = "", required = true, in = ParameterIn.PATH) @PathVariable("stream_id") Long streamId
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

    /**
     * GET /v1/camera_streams/{stream_id} : Get camera_streams by stream_id
     *
     * @param streamId  (required)
     * @return OK (status code 200)
     *         or Not Found (status code 404)
     *         or Internal Server Error (status code 500)
     * @see CameraStreamsApi#getCameraStreams
     */
    @Override
    public CameraStreams getCameraStreams(
        @Parameter(name = "stream_id", description = "", required = true, in = ParameterIn.PATH) @PathVariable("stream_id") Long streamId
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

    /**
     * GET /v1/camera_streams : List camera_streams
     *
     * @param page  (optional)
     * @param size  (optional)
     * @param sort e.g. created_at,desc (optional)
     * @return OK (status code 200)
     *         or Bad Request (status code 400)
     *         or Internal Server Error (status code 500)
     * @see CameraStreamsApi#listCameraStreams
     */
    @Override
    public PageOfCameraStreams listCameraStreams(
        @Parameter(name = "page", description = "", in = ParameterIn.QUERY) @RequestParam(value = "page", required = false) Integer page,
        @Parameter(name = "size", description = "", in = ParameterIn.QUERY) @RequestParam(value = "size", required = false) Integer size,
        @Parameter(name = "sort", description = "e.g. created_at,desc", in = ParameterIn.QUERY) @RequestParam(value = "sort", required = false) String sort
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

    /**
     * PUT /v1/camera_streams/{stream_id} : Update camera_streams by stream_id
     *
     * @param streamId  (required)
     * @param cameraStreamsUpdateRequest  (required)
     * @return OK (status code 200)
     *         or Bad Request (status code 400)
     *         or Not Found (status code 404)
     *         or Conflict (status code 409)
     *         or Internal Server Error (status code 500)
     * @see CameraStreamsApi#updateCameraStreams
     */
    @Override
    public CameraStreams updateCameraStreams(
        @Parameter(name = "stream_id", description = "", required = true, in = ParameterIn.PATH) @PathVariable("stream_id") Long streamId,
        @Parameter(name = "CameraStreamsUpdateRequest", description = "", required = true) @RequestBody CameraStreamsUpdateRequest cameraStreamsUpdateRequest
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

}
