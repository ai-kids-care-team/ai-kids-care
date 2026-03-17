package com.ai_kids_care.v1.controller;

import com.ai_kids_care.v1.api.ChildrenApi;
import com.ai_kids_care.v1.entity.Children;
import com.ai_kids_care.v1.dto.ChildrenCreateRequest;
import com.ai_kids_care.v1.dto.ChildrenUpdateRequest;
import com.ai_kids_care.v1.entity.PageOfChildren;

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
public class ChildrenApiController implements ChildrenApi {

    /**
     * POST /v1/children : Create children
     *
     * @param childrenCreateRequest  (required)
     * @return Created (status code 201)
     *         or Bad Request (status code 400)
     *         or Conflict (status code 409)
     *         or Internal Server Error (status code 500)
     * @see ChildrenApi#createChildren
     */
    @Override
    public Children createChildren(
        @Parameter(name = "ChildrenCreateRequest", description = "", required = true) @RequestBody ChildrenCreateRequest childrenCreateRequest
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

    /**
     * DELETE /v1/children/{child_id} : Delete children by child_id
     *
     * @param childId  (required)
     * @return No Content (status code 204)
     *         or Not Found (status code 404)
     *         or Internal Server Error (status code 500)
     * @see ChildrenApi#deleteChildren
     */
    @Override
    public void deleteChildren(
        @Parameter(name = "child_id", description = "", required = true, in = ParameterIn.PATH) @PathVariable("child_id") Long childId
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

    /**
     * GET /v1/children/{child_id} : Get children by child_id
     *
     * @param childId  (required)
     * @return OK (status code 200)
     *         or Not Found (status code 404)
     *         or Internal Server Error (status code 500)
     * @see ChildrenApi#getChildren
     */
    @Override
    public Children getChildren(
        @Parameter(name = "child_id", description = "", required = true, in = ParameterIn.PATH) @PathVariable("child_id") Long childId
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

    /**
     * GET /v1/children : List children
     *
     * @param page  (optional)
     * @param size  (optional)
     * @param sort e.g. created_at,desc (optional)
     * @return OK (status code 200)
     *         or Bad Request (status code 400)
     *         or Internal Server Error (status code 500)
     * @see ChildrenApi#listChildren
     */
    @Override
    public PageOfChildren listChildren(
        @Parameter(name = "page", description = "", in = ParameterIn.QUERY) @RequestParam(value = "page", required = false) Integer page,
        @Parameter(name = "size", description = "", in = ParameterIn.QUERY) @RequestParam(value = "size", required = false) Integer size,
        @Parameter(name = "sort", description = "e.g. created_at,desc", in = ParameterIn.QUERY) @RequestParam(value = "sort", required = false) String sort
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

    /**
     * PUT /v1/children/{child_id} : Update children by child_id
     *
     * @param childId  (required)
     * @param childrenUpdateRequest  (required)
     * @return OK (status code 200)
     *         or Bad Request (status code 400)
     *         or Not Found (status code 404)
     *         or Conflict (status code 409)
     *         or Internal Server Error (status code 500)
     * @see ChildrenApi#updateChildren
     */
    @Override
    public Children updateChildren(
        @Parameter(name = "child_id", description = "", required = true, in = ParameterIn.PATH) @PathVariable("child_id") Long childId,
        @Parameter(name = "ChildrenUpdateRequest", description = "", required = true) @RequestBody ChildrenUpdateRequest childrenUpdateRequest
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

}
