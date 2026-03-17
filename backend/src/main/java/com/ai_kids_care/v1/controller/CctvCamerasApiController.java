package com.ai_kids_care.v1.controller;

import com.ai_kids_care.v1.api.CctvCamerasApi;
import com.ai_kids_care.v1.entity.CctvCameras;
import com.ai_kids_care.v1.dto.CctvCamerasCreateRequest;
import com.ai_kids_care.v1.dto.CctvCamerasUpdateRequest;
import com.ai_kids_care.v1.entity.PageOfCctvCameras;

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
public class CctvCamerasApiController implements CctvCamerasApi {

    /**
     * POST /v1/cctv_cameras : Create cctv_cameras
     *
     * @param cctvCamerasCreateRequest  (required)
     * @return Created (status code 201)
     *         or Bad Request (status code 400)
     *         or Conflict (status code 409)
     *         or Internal Server Error (status code 500)
     * @see CctvCamerasApi#createCctvCameras
     */
    @Override
    public CctvCameras createCctvCameras(
        @Parameter(name = "CctvCamerasCreateRequest", description = "", required = true) @RequestBody CctvCamerasCreateRequest cctvCamerasCreateRequest
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

    /**
     * DELETE /v1/cctv_cameras/{camera_id} : Delete cctv_cameras by camera_id
     *
     * @param cameraId  (required)
     * @return No Content (status code 204)
     *         or Not Found (status code 404)
     *         or Internal Server Error (status code 500)
     * @see CctvCamerasApi#deleteCctvCameras
     */
    @Override
    public void deleteCctvCameras(
        @Parameter(name = "camera_id", description = "", required = true, in = ParameterIn.PATH) @PathVariable("camera_id") Long cameraId
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

    /**
     * GET /v1/cctv_cameras/{camera_id} : Get cctv_cameras by camera_id
     *
     * @param cameraId  (required)
     * @return OK (status code 200)
     *         or Not Found (status code 404)
     *         or Internal Server Error (status code 500)
     * @see CctvCamerasApi#getCctvCameras
     */
    @Override
    public CctvCameras getCctvCameras(
        @Parameter(name = "camera_id", description = "", required = true, in = ParameterIn.PATH) @PathVariable("camera_id") Long cameraId
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

    /**
     * GET /v1/cctv_cameras : List cctv_cameras
     *
     * @param page  (optional)
     * @param size  (optional)
     * @param sort e.g. created_at,desc (optional)
     * @return OK (status code 200)
     *         or Bad Request (status code 400)
     *         or Internal Server Error (status code 500)
     * @see CctvCamerasApi#listCctvCameras
     */
    @Override
    public PageOfCctvCameras listCctvCameras(
        @Parameter(name = "page", description = "", in = ParameterIn.QUERY) @RequestParam(value = "page", required = false) Integer page,
        @Parameter(name = "size", description = "", in = ParameterIn.QUERY) @RequestParam(value = "size", required = false) Integer size,
        @Parameter(name = "sort", description = "e.g. created_at,desc", in = ParameterIn.QUERY) @RequestParam(value = "sort", required = false) String sort
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

    /**
     * PUT /v1/cctv_cameras/{camera_id} : Update cctv_cameras by camera_id
     *
     * @param cameraId  (required)
     * @param cctvCamerasUpdateRequest  (required)
     * @return OK (status code 200)
     *         or Bad Request (status code 400)
     *         or Not Found (status code 404)
     *         or Conflict (status code 409)
     *         or Internal Server Error (status code 500)
     * @see CctvCamerasApi#updateCctvCameras
     */
    @Override
    public CctvCameras updateCctvCameras(
        @Parameter(name = "camera_id", description = "", required = true, in = ParameterIn.PATH) @PathVariable("camera_id") Long cameraId,
        @Parameter(name = "CctvCamerasUpdateRequest", description = "", required = true) @RequestBody CctvCamerasUpdateRequest cctvCamerasUpdateRequest
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

}
