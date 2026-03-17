package com.ai_kids_care.v1.controller;

import com.ai_kids_care.v1.api.UsersApi;
import com.ai_kids_care.v1.dto.PageOfUsers;
import com.ai_kids_care.v1.entity.Users;
import com.ai_kids_care.v1.dto.UsersCreateRequest;
import com.ai_kids_care.v1.dto.UsersUpdateRequest;

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
public class UsersApiController implements UsersApi {

    /**
     * POST /v1/users : Create users
     *
     * @param usersCreateRequest  (required)
     * @return Created (status code 201)
     *         or Bad Request (status code 400)
     *         or Conflict (status code 409)
     *         or Internal Server Error (status code 500)
     * @see UsersApi#createUsers
     */
    @Override
    public Users createUsers(
        @Parameter(name = "UsersCreateRequest", description = "", required = true) @RequestBody UsersCreateRequest usersCreateRequest
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

    /**
     * DELETE /v1/users/{user_id} : Delete users by user_id
     *
     * @param userId  (required)
     * @return No Content (status code 204)
     *         or Not Found (status code 404)
     *         or Internal Server Error (status code 500)
     * @see UsersApi#deleteUsers
     */
    @Override
    public void deleteUsers(
        @Parameter(name = "user_id", description = "", required = true, in = ParameterIn.PATH) @PathVariable("user_id") Long userId
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

    /**
     * GET /v1/users/{user_id} : Get users by user_id
     *
     * @param userId  (required)
     * @return OK (status code 200)
     *         or Not Found (status code 404)
     *         or Internal Server Error (status code 500)
     * @see UsersApi#getUsers
     */
    @Override
    public Users getUsers(
        @Parameter(name = "user_id", description = "", required = true, in = ParameterIn.PATH) @PathVariable("user_id") Long userId
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

    /**
     * GET /v1/users : List users
     *
     * @param page  (optional)
     * @param size  (optional)
     * @param sort e.g. created_at,desc (optional)
     * @return OK (status code 200)
     *         or Bad Request (status code 400)
     *         or Internal Server Error (status code 500)
     * @see UsersApi#listUsers
     */
    @Override
    public PageOfUsers listUsers(
        @Parameter(name = "page", description = "", in = ParameterIn.QUERY) @RequestParam(value = "page", required = false) Integer page,
        @Parameter(name = "size", description = "", in = ParameterIn.QUERY) @RequestParam(value = "size", required = false) Integer size,
        @Parameter(name = "sort", description = "e.g. created_at,desc", in = ParameterIn.QUERY) @RequestParam(value = "sort", required = false) String sort
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

    /**
     * PUT /v1/users/{user_id} : Update users by user_id
     *
     * @param userId  (required)
     * @param usersUpdateRequest  (required)
     * @return OK (status code 200)
     *         or Bad Request (status code 400)
     *         or Not Found (status code 404)
     *         or Conflict (status code 409)
     *         or Internal Server Error (status code 500)
     * @see UsersApi#updateUsers
     */
    @Override
    public Users updateUsers(
        @Parameter(name = "user_id", description = "", required = true, in = ParameterIn.PATH) @PathVariable("user_id") Long userId,
        @Parameter(name = "UsersUpdateRequest", description = "", required = true) @RequestBody UsersUpdateRequest usersUpdateRequest
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

}
