package com.ai_kids_care.v1.controller;

import com.ai_kids_care.v1.api.NotificationsApi;
import com.ai_kids_care.v1.entity.Notifications;
import com.ai_kids_care.v1.dto.NotificationsCreateRequest;
import com.ai_kids_care.v1.dto.NotificationsUpdateRequest;
import com.ai_kids_care.v1.dto.PageOfNotifications;

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
public class NotificationsApiController implements NotificationsApi {

    /**
     * POST /v1/notifications : Create notifications
     *
     * @param notificationsCreateRequest  (required)
     * @return Created (status code 201)
     *         or Bad Request (status code 400)
     *         or Conflict (status code 409)
     *         or Internal Server Error (status code 500)
     * @see NotificationsApi#createNotifications
     */
    @Override
    public Notifications createNotifications(
        @Parameter(name = "NotificationsCreateRequest", description = "", required = true) @RequestBody NotificationsCreateRequest notificationsCreateRequest
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

    /**
     * DELETE /v1/notifications/{notification_id} : Delete notifications by notification_id
     *
     * @param notificationId  (required)
     * @return No Content (status code 204)
     *         or Not Found (status code 404)
     *         or Internal Server Error (status code 500)
     * @see NotificationsApi#deleteNotifications
     */
    @Override
    public void deleteNotifications(
        @Parameter(name = "notification_id", description = "", required = true, in = ParameterIn.PATH) @PathVariable("notification_id") Long notificationId
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

    /**
     * GET /v1/notifications/{notification_id} : Get notifications by notification_id
     *
     * @param notificationId  (required)
     * @return OK (status code 200)
     *         or Not Found (status code 404)
     *         or Internal Server Error (status code 500)
     * @see NotificationsApi#getNotifications
     */
    @Override
    public Notifications getNotifications(
        @Parameter(name = "notification_id", description = "", required = true, in = ParameterIn.PATH) @PathVariable("notification_id") Long notificationId
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

    /**
     * GET /v1/notifications : List notifications
     *
     * @param page  (optional)
     * @param size  (optional)
     * @param sort e.g. created_at,desc (optional)
     * @return OK (status code 200)
     *         or Bad Request (status code 400)
     *         or Internal Server Error (status code 500)
     * @see NotificationsApi#listNotifications
     */
    @Override
    public PageOfNotifications listNotifications(
        @Parameter(name = "page", description = "", in = ParameterIn.QUERY) @RequestParam(value = "page", required = false) Integer page,
        @Parameter(name = "size", description = "", in = ParameterIn.QUERY) @RequestParam(value = "size", required = false) Integer size,
        @Parameter(name = "sort", description = "e.g. created_at,desc", in = ParameterIn.QUERY) @RequestParam(value = "sort", required = false) String sort
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

    /**
     * PUT /v1/notifications/{notification_id} : Update notifications by notification_id
     *
     * @param notificationId  (required)
     * @param notificationsUpdateRequest  (required)
     * @return OK (status code 200)
     *         or Bad Request (status code 400)
     *         or Not Found (status code 404)
     *         or Conflict (status code 409)
     *         or Internal Server Error (status code 500)
     * @see NotificationsApi#updateNotifications
     */
    @Override
    public Notifications updateNotifications(
        @Parameter(name = "notification_id", description = "", required = true, in = ParameterIn.PATH) @PathVariable("notification_id") Long notificationId,
        @Parameter(name = "NotificationsUpdateRequest", description = "", required = true) @RequestBody NotificationsUpdateRequest notificationsUpdateRequest
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

}
