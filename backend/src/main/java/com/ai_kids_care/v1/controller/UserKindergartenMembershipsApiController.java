package com.ai_kids_care.v1.controller;

import com.ai_kids_care.v1.api.UserKindergartenMembershipsApi;
import com.ai_kids_care.v1.dto.PageOfUserKindergartenMemberships;
import com.ai_kids_care.v1.entity.UserKindergartenMemberships;
import com.ai_kids_care.v1.dto.UserKindergartenMembershipsCreateRequest;
import com.ai_kids_care.v1.dto.UserKindergartenMembershipsUpdateRequest;

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
public class UserKindergartenMembershipsApiController implements UserKindergartenMembershipsApi {

    /**
     * POST /v1/user_kindergarten_memberships : Create user_kindergarten_memberships
     *
     * @param userKindergartenMembershipsCreateRequest  (required)
     * @return Created (status code 201)
     *         or Bad Request (status code 400)
     *         or Conflict (status code 409)
     *         or Internal Server Error (status code 500)
     * @see UserKindergartenMembershipsApi#createUserKindergartenMemberships
     */
    @Override
    public UserKindergartenMemberships createUserKindergartenMemberships(
        @Parameter(name = "UserKindergartenMembershipsCreateRequest", description = "", required = true) @RequestBody UserKindergartenMembershipsCreateRequest userKindergartenMembershipsCreateRequest
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

    /**
     * DELETE /v1/user_kindergarten_memberships/{membership_id} : Delete user_kindergarten_memberships by membership_id
     *
     * @param membershipId  (required)
     * @return No Content (status code 204)
     *         or Not Found (status code 404)
     *         or Internal Server Error (status code 500)
     * @see UserKindergartenMembershipsApi#deleteUserKindergartenMemberships
     */
    @Override
    public void deleteUserKindergartenMemberships(
        @Parameter(name = "membership_id", description = "", required = true, in = ParameterIn.PATH) @PathVariable("membership_id") Long membershipId
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

    /**
     * GET /v1/user_kindergarten_memberships/{membership_id} : Get user_kindergarten_memberships by membership_id
     *
     * @param membershipId  (required)
     * @return OK (status code 200)
     *         or Not Found (status code 404)
     *         or Internal Server Error (status code 500)
     * @see UserKindergartenMembershipsApi#getUserKindergartenMemberships
     */
    @Override
    public UserKindergartenMemberships getUserKindergartenMemberships(
        @Parameter(name = "membership_id", description = "", required = true, in = ParameterIn.PATH) @PathVariable("membership_id") Long membershipId
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

    /**
     * GET /v1/user_kindergarten_memberships : List user_kindergarten_memberships
     *
     * @param page  (optional)
     * @param size  (optional)
     * @param sort e.g. created_at,desc (optional)
     * @return OK (status code 200)
     *         or Bad Request (status code 400)
     *         or Internal Server Error (status code 500)
     * @see UserKindergartenMembershipsApi#listUserKindergartenMemberships
     */
    @Override
    public PageOfUserKindergartenMemberships listUserKindergartenMemberships(
        @Parameter(name = "page", description = "", in = ParameterIn.QUERY) @RequestParam(value = "page", required = false) Integer page,
        @Parameter(name = "size", description = "", in = ParameterIn.QUERY) @RequestParam(value = "size", required = false) Integer size,
        @Parameter(name = "sort", description = "e.g. created_at,desc", in = ParameterIn.QUERY) @RequestParam(value = "sort", required = false) String sort
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

    /**
     * PUT /v1/user_kindergarten_memberships/{membership_id} : Update user_kindergarten_memberships by membership_id
     *
     * @param membershipId  (required)
     * @param userKindergartenMembershipsUpdateRequest  (required)
     * @return OK (status code 200)
     *         or Bad Request (status code 400)
     *         or Not Found (status code 404)
     *         or Conflict (status code 409)
     *         or Internal Server Error (status code 500)
     * @see UserKindergartenMembershipsApi#updateUserKindergartenMemberships
     */
    @Override
    public UserKindergartenMemberships updateUserKindergartenMemberships(
        @Parameter(name = "membership_id", description = "", required = true, in = ParameterIn.PATH) @PathVariable("membership_id") Long membershipId,
        @Parameter(name = "UserKindergartenMembershipsUpdateRequest", description = "", required = true) @RequestBody UserKindergartenMembershipsUpdateRequest userKindergartenMembershipsUpdateRequest
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

}
