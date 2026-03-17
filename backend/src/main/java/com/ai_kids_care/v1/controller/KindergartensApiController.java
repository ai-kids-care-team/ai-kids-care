package com.ai_kids_care.v1.controller;

import com.ai_kids_care.v1.api.KindergartensApi;
import com.ai_kids_care.v1.entity.Kindergarten;
import com.ai_kids_care.v1.dto.KindergartensCreateRequest;
import com.ai_kids_care.v1.dto.KindergartensUpdateRequest;
import com.ai_kids_care.v1.dto.PageOfKindergartens;

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
public class KindergartensApiController implements KindergartensApi {

    /**
     * POST /v1/kindergartens : Create kindergartens
     *
     * @param kindergartensCreateRequest  (required)
     * @return Created (status code 201)
     *         or Bad Request (status code 400)
     *         or Conflict (status code 409)
     *         or Internal Server Error (status code 500)
     * @see KindergartensApi#createKindergartens
     */
    @Override
    public Kindergarten createKindergartens(
        @Parameter(name = "KindergartensCreateRequest", description = "", required = true) @RequestBody KindergartensCreateRequest kindergartensCreateRequest
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

    /**
     * DELETE /v1/kindergartens/{kindergarten_id} : Delete kindergartens by kindergarten_id
     *
     * @param kindergartenId  (required)
     * @return No Content (status code 204)
     *         or Not Found (status code 404)
     *         or Internal Server Error (status code 500)
     * @see KindergartensApi#deleteKindergartens
     */
    @Override
    public void deleteKindergartens(
        @Parameter(name = "kindergarten_id", description = "", required = true, in = ParameterIn.PATH) @PathVariable("kindergarten_id") Long kindergartenId
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

    /**
     * GET /v1/kindergartens/{kindergarten_id} : Get kindergartens by kindergarten_id
     *
     * @param kindergartenId  (required)
     * @return OK (status code 200)
     *         or Not Found (status code 404)
     *         or Internal Server Error (status code 500)
     * @see KindergartensApi#getKindergartens
     */
    @Override
    public Kindergarten getKindergartens(
        @Parameter(name = "kindergarten_id", description = "", required = true, in = ParameterIn.PATH) @PathVariable("kindergarten_id") Long kindergartenId
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

    /**
     * GET /v1/kindergartens : List kindergartens
     *
     * @param page  (optional)
     * @param size  (optional)
     * @param sort e.g. created_at,desc (optional)
     * @return OK (status code 200)
     *         or Bad Request (status code 400)
     *         or Internal Server Error (status code 500)
     * @see KindergartensApi#listKindergartens
     */
    @Override
    public PageOfKindergartens listKindergartens(
        @Parameter(name = "page", description = "", in = ParameterIn.QUERY) @RequestParam(value = "page", required = false) Integer page,
        @Parameter(name = "size", description = "", in = ParameterIn.QUERY) @RequestParam(value = "size", required = false) Integer size,
        @Parameter(name = "sort", description = "e.g. created_at,desc", in = ParameterIn.QUERY) @RequestParam(value = "sort", required = false) String sort
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

    /**
     * PUT /v1/kindergartens/{kindergarten_id} : Update kindergartens by kindergarten_id
     *
     * @param kindergartenId  (required)
     * @param kindergartensUpdateRequest  (required)
     * @return OK (status code 200)
     *         or Bad Request (status code 400)
     *         or Not Found (status code 404)
     *         or Conflict (status code 409)
     *         or Internal Server Error (status code 500)
     * @see KindergartensApi#updateKindergartens
     */
    @Override
    public Kindergarten updateKindergartens(
        @Parameter(name = "kindergarten_id", description = "", required = true, in = ParameterIn.PATH) @PathVariable("kindergarten_id") Long kindergartenId,
        @Parameter(name = "KindergartensUpdateRequest", description = "", required = true) @RequestBody KindergartensUpdateRequest kindergartensUpdateRequest
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

}
