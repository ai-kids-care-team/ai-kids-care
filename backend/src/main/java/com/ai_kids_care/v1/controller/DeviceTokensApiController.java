package com.ai_kids_care.v1.controller;

import com.ai_kids_care.v1.api.DeviceTokensApi;
import com.ai_kids_care.v1.entity.DeviceToken;
import com.ai_kids_care.v1.dto.DeviceTokensCreateRequest;
import com.ai_kids_care.v1.dto.DeviceTokensUpdateRequest;
import com.ai_kids_care.v1.dto.PageOfDeviceTokens;

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
public class DeviceTokensApiController implements DeviceTokensApi {

    /**
     * POST /v1/device_tokens : Create device_tokens
     *
     * @param deviceTokensCreateRequest  (required)
     * @return Created (status code 201)
     *         or Bad Request (status code 400)
     *         or Conflict (status code 409)
     *         or Internal Server Error (status code 500)
     * @see DeviceTokensApi#createDeviceTokens
     */
    @Override
    public DeviceToken createDeviceTokens(
        @Parameter(name = "DeviceTokensCreateRequest", description = "", required = true) @RequestBody DeviceTokensCreateRequest deviceTokensCreateRequest
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

    /**
     * DELETE /v1/device_tokens/{device_id} : Delete device_tokens by device_id
     *
     * @param deviceId  (required)
     * @return No Content (status code 204)
     *         or Not Found (status code 404)
     *         or Internal Server Error (status code 500)
     * @see DeviceTokensApi#deleteDeviceTokens
     */
    @Override
    public void deleteDeviceTokens(
        @Parameter(name = "device_id", description = "", required = true, in = ParameterIn.PATH) @PathVariable("device_id") Long deviceId
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

    /**
     * GET /v1/device_tokens/{device_id} : Get device_tokens by device_id
     *
     * @param deviceId  (required)
     * @return OK (status code 200)
     *         or Not Found (status code 404)
     *         or Internal Server Error (status code 500)
     * @see DeviceTokensApi#getDeviceTokens
     */
    @Override
    public DeviceToken getDeviceTokens(
        @Parameter(name = "device_id", description = "", required = true, in = ParameterIn.PATH) @PathVariable("device_id") Long deviceId
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

    /**
     * GET /v1/device_tokens : List device_tokens
     *
     * @param page  (optional)
     * @param size  (optional)
     * @param sort e.g. created_at,desc (optional)
     * @return OK (status code 200)
     *         or Bad Request (status code 400)
     *         or Internal Server Error (status code 500)
     * @see DeviceTokensApi#listDeviceTokens
     */
    @Override
    public PageOfDeviceTokens listDeviceTokens(
        @Parameter(name = "page", description = "", in = ParameterIn.QUERY) @RequestParam(value = "page", required = false) Integer page,
        @Parameter(name = "size", description = "", in = ParameterIn.QUERY) @RequestParam(value = "size", required = false) Integer size,
        @Parameter(name = "sort", description = "e.g. created_at,desc", in = ParameterIn.QUERY) @RequestParam(value = "sort", required = false) String sort
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

    /**
     * PUT /v1/device_tokens/{device_id} : Update device_tokens by device_id
     *
     * @param deviceId  (required)
     * @param deviceTokensUpdateRequest  (required)
     * @return OK (status code 200)
     *         or Bad Request (status code 400)
     *         or Not Found (status code 404)
     *         or Conflict (status code 409)
     *         or Internal Server Error (status code 500)
     * @see DeviceTokensApi#updateDeviceTokens
     */
    @Override
    public DeviceToken updateDeviceTokens(
        @Parameter(name = "device_id", description = "", required = true, in = ParameterIn.PATH) @PathVariable("device_id") Long deviceId,
        @Parameter(name = "DeviceTokensUpdateRequest", description = "", required = true) @RequestBody DeviceTokensUpdateRequest deviceTokensUpdateRequest
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

}
