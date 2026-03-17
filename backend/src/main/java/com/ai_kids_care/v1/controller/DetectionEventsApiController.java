package com.ai_kids_care.v1.controller;

import com.ai_kids_care.v1.api.DetectionEventsApi;
import com.ai_kids_care.v1.entity.DetectionEvents;
import com.ai_kids_care.v1.dto.DetectionEventsCreateRequest;
import com.ai_kids_care.v1.dto.DetectionEventsUpdateRequest;
import com.ai_kids_care.v1.dto.PageOfDetectionEvents;

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
public class DetectionEventsApiController implements DetectionEventsApi {

    /**
     * POST /v1/detection_events : Create detection_events
     *
     * @param detectionEventsCreateRequest  (required)
     * @return Created (status code 201)
     *         or Bad Request (status code 400)
     *         or Conflict (status code 409)
     *         or Internal Server Error (status code 500)
     * @see DetectionEventsApi#createDetectionEvents
     */
    @Override
    public DetectionEvents createDetectionEvents(
        @Parameter(name = "DetectionEventsCreateRequest", description = "", required = true) @RequestBody DetectionEventsCreateRequest detectionEventsCreateRequest
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

    /**
     * DELETE /v1/detection_events/{event_id} : Delete detection_events by event_id
     *
     * @param eventId  (required)
     * @return No Content (status code 204)
     *         or Not Found (status code 404)
     *         or Internal Server Error (status code 500)
     * @see DetectionEventsApi#deleteDetectionEvents
     */
    @Override
    public void deleteDetectionEvents(
        @Parameter(name = "event_id", description = "", required = true, in = ParameterIn.PATH) @PathVariable("event_id") Long eventId
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

    /**
     * GET /v1/detection_events/{event_id} : Get detection_events by event_id
     *
     * @param eventId  (required)
     * @return OK (status code 200)
     *         or Not Found (status code 404)
     *         or Internal Server Error (status code 500)
     * @see DetectionEventsApi#getDetectionEvents
     */
    @Override
    public DetectionEvents getDetectionEvents(
        @Parameter(name = "event_id", description = "", required = true, in = ParameterIn.PATH) @PathVariable("event_id") Long eventId
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

    /**
     * GET /v1/detection_events : List detection_events
     *
     * @param page  (optional)
     * @param size  (optional)
     * @param sort e.g. created_at,desc (optional)
     * @return OK (status code 200)
     *         or Bad Request (status code 400)
     *         or Internal Server Error (status code 500)
     * @see DetectionEventsApi#listDetectionEvents
     */
    @Override
    public PageOfDetectionEvents listDetectionEvents(
        @Parameter(name = "page", description = "", in = ParameterIn.QUERY) @RequestParam(value = "page", required = false) Integer page,
        @Parameter(name = "size", description = "", in = ParameterIn.QUERY) @RequestParam(value = "size", required = false) Integer size,
        @Parameter(name = "sort", description = "e.g. created_at,desc", in = ParameterIn.QUERY) @RequestParam(value = "sort", required = false) String sort
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

    /**
     * PUT /v1/detection_events/{event_id} : Update detection_events by event_id
     *
     * @param eventId  (required)
     * @param detectionEventsUpdateRequest  (required)
     * @return OK (status code 200)
     *         or Bad Request (status code 400)
     *         or Not Found (status code 404)
     *         or Conflict (status code 409)
     *         or Internal Server Error (status code 500)
     * @see DetectionEventsApi#updateDetectionEvents
     */
    @Override
    public DetectionEvents updateDetectionEvents(
        @Parameter(name = "event_id", description = "", required = true, in = ParameterIn.PATH) @PathVariable("event_id") Long eventId,
        @Parameter(name = "DetectionEventsUpdateRequest", description = "", required = true) @RequestBody DetectionEventsUpdateRequest detectionEventsUpdateRequest
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

}
