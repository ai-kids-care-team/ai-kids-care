package com.ai_kids_care.v1.controller;

import com.ai_kids_care.v1.api.NotificationRulesApi;
import com.ai_kids_care.v1.entity.NotificationRule;
import com.ai_kids_care.v1.dto.NotificationRulesCreateRequest;
import com.ai_kids_care.v1.dto.NotificationRulesUpdateRequest;
import com.ai_kids_care.v1.dto.PageOfNotificationRules;

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
public class NotificationRulesApiController implements NotificationRulesApi {

    /**
     * POST /v1/notification_rules : Create notification_rules
     *
     * @param notificationRulesCreateRequest  (required)
     * @return Created (status code 201)
     *         or Bad Request (status code 400)
     *         or Conflict (status code 409)
     *         or Internal Server Error (status code 500)
     * @see NotificationRulesApi#createNotificationRules
     */
    @Override
    public NotificationRule createNotificationRules(
        @Parameter(name = "NotificationRulesCreateRequest", description = "", required = true) @RequestBody NotificationRulesCreateRequest notificationRulesCreateRequest
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

    /**
     * DELETE /v1/notification_rules/{rule_id} : Delete notification_rules by rule_id
     *
     * @param ruleId  (required)
     * @return No Content (status code 204)
     *         or Not Found (status code 404)
     *         or Internal Server Error (status code 500)
     * @see NotificationRulesApi#deleteNotificationRules
     */
    @Override
    public void deleteNotificationRules(
        @Parameter(name = "rule_id", description = "", required = true, in = ParameterIn.PATH) @PathVariable("rule_id") Long ruleId
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

    /**
     * GET /v1/notification_rules/{rule_id} : Get notification_rules by rule_id
     *
     * @param ruleId  (required)
     * @return OK (status code 200)
     *         or Not Found (status code 404)
     *         or Internal Server Error (status code 500)
     * @see NotificationRulesApi#getNotificationRules
     */
    @Override
    public NotificationRule getNotificationRules(
        @Parameter(name = "rule_id", description = "", required = true, in = ParameterIn.PATH) @PathVariable("rule_id") Long ruleId
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

    /**
     * GET /v1/notification_rules : List notification_rules
     *
     * @param page  (optional)
     * @param size  (optional)
     * @param sort e.g. created_at,desc (optional)
     * @return OK (status code 200)
     *         or Bad Request (status code 400)
     *         or Internal Server Error (status code 500)
     * @see NotificationRulesApi#listNotificationRules
     */
    @Override
    public PageOfNotificationRules listNotificationRules(
        @Parameter(name = "page", description = "", in = ParameterIn.QUERY) @RequestParam(value = "page", required = false) Integer page,
        @Parameter(name = "size", description = "", in = ParameterIn.QUERY) @RequestParam(value = "size", required = false) Integer size,
        @Parameter(name = "sort", description = "e.g. created_at,desc", in = ParameterIn.QUERY) @RequestParam(value = "sort", required = false) String sort
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

    /**
     * PUT /v1/notification_rules/{rule_id} : Update notification_rules by rule_id
     *
     * @param ruleId  (required)
     * @param notificationRulesUpdateRequest  (required)
     * @return OK (status code 200)
     *         or Bad Request (status code 400)
     *         or Not Found (status code 404)
     *         or Conflict (status code 409)
     *         or Internal Server Error (status code 500)
     * @see NotificationRulesApi#updateNotificationRules
     */
    @Override
    public NotificationRule updateNotificationRules(
        @Parameter(name = "rule_id", description = "", required = true, in = ParameterIn.PATH) @PathVariable("rule_id") Long ruleId,
        @Parameter(name = "NotificationRulesUpdateRequest", description = "", required = true) @RequestBody NotificationRulesUpdateRequest notificationRulesUpdateRequest
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

}
