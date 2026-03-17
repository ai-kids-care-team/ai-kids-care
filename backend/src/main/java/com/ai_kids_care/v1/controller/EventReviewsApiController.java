package com.ai_kids_care.v1.controller;

import com.ai_kids_care.v1.api.EventReviewsApi;
import com.ai_kids_care.v1.entity.EventReviews;
import com.ai_kids_care.v1.dto.EventReviewsCreateRequest;
import com.ai_kids_care.v1.dto.EventReviewsUpdateRequest;
import com.ai_kids_care.v1.dto.PageOfEventReviews;

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
public class EventReviewsApiController implements EventReviewsApi {

    /**
     * POST /v1/event_reviews : Create event_reviews
     *
     * @param eventReviewsCreateRequest  (required)
     * @return Created (status code 201)
     *         or Bad Request (status code 400)
     *         or Conflict (status code 409)
     *         or Internal Server Error (status code 500)
     * @see EventReviewsApi#createEventReviews
     */
    @Override
    public EventReviews createEventReviews(
        @Parameter(name = "EventReviewsCreateRequest", description = "", required = true) @RequestBody EventReviewsCreateRequest eventReviewsCreateRequest
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

    /**
     * DELETE /v1/event_reviews/{review_id} : Delete event_reviews by review_id
     *
     * @param reviewId  (required)
     * @return No Content (status code 204)
     *         or Not Found (status code 404)
     *         or Internal Server Error (status code 500)
     * @see EventReviewsApi#deleteEventReviews
     */
    @Override
    public void deleteEventReviews(
        @Parameter(name = "review_id", description = "", required = true, in = ParameterIn.PATH) @PathVariable("review_id") Long reviewId
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

    /**
     * GET /v1/event_reviews/{review_id} : Get event_reviews by review_id
     *
     * @param reviewId  (required)
     * @return OK (status code 200)
     *         or Not Found (status code 404)
     *         or Internal Server Error (status code 500)
     * @see EventReviewsApi#getEventReviews
     */
    @Override
    public EventReviews getEventReviews(
        @Parameter(name = "review_id", description = "", required = true, in = ParameterIn.PATH) @PathVariable("review_id") Long reviewId
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

    /**
     * GET /v1/event_reviews : List event_reviews
     *
     * @param page  (optional)
     * @param size  (optional)
     * @param sort e.g. created_at,desc (optional)
     * @return OK (status code 200)
     *         or Bad Request (status code 400)
     *         or Internal Server Error (status code 500)
     * @see EventReviewsApi#listEventReviews
     */
    @Override
    public PageOfEventReviews listEventReviews(
        @Parameter(name = "page", description = "", in = ParameterIn.QUERY) @RequestParam(value = "page", required = false) Integer page,
        @Parameter(name = "size", description = "", in = ParameterIn.QUERY) @RequestParam(value = "size", required = false) Integer size,
        @Parameter(name = "sort", description = "e.g. created_at,desc", in = ParameterIn.QUERY) @RequestParam(value = "sort", required = false) String sort
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

    /**
     * PUT /v1/event_reviews/{review_id} : Update event_reviews by review_id
     *
     * @param reviewId  (required)
     * @param eventReviewsUpdateRequest  (required)
     * @return OK (status code 200)
     *         or Bad Request (status code 400)
     *         or Not Found (status code 404)
     *         or Conflict (status code 409)
     *         or Internal Server Error (status code 500)
     * @see EventReviewsApi#updateEventReviews
     */
    @Override
    public EventReviews updateEventReviews(
        @Parameter(name = "review_id", description = "", required = true, in = ParameterIn.PATH) @PathVariable("review_id") Long reviewId,
        @Parameter(name = "EventReviewsUpdateRequest", description = "", required = true) @RequestBody EventReviewsUpdateRequest eventReviewsUpdateRequest
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

}
