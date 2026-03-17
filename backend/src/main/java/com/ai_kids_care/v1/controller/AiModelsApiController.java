package com.ai_kids_care.v1.controller;

import com.ai_kids_care.v1.api.AiModelsApi;
import com.ai_kids_care.v1.entity.AiModels;
import com.ai_kids_care.v1.dto.AiModelsCreateRequest;
import com.ai_kids_care.v1.dto.AiModelsUpdateRequest;
import com.ai_kids_care.v1.entity.PageOfAiModels;

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
public class AiModelsApiController implements AiModelsApi {

    /**
     * POST /v1/ai_models : Create ai_models
     *
     * @param aiModelsCreateRequest  (required)
     * @return Created (status code 201)
     *         or Bad Request (status code 400)
     *         or Conflict (status code 409)
     *         or Internal Server Error (status code 500)
     * @see AiModelsApi#createAiModels
     */
    @Override
    public AiModels createAiModels(
        @Parameter(name = "AiModelsCreateRequest", description = "", required = true) @RequestBody AiModelsCreateRequest aiModelsCreateRequest
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

    /**
     * DELETE /v1/ai_models/{model_id} : Delete ai_models by model_id
     *
     * @param modelId  (required)
     * @return No Content (status code 204)
     *         or Not Found (status code 404)
     *         or Internal Server Error (status code 500)
     * @see AiModelsApi#deleteAiModels
     */
    @Override
    public void deleteAiModels(
        @Parameter(name = "model_id", description = "", required = true, in = ParameterIn.PATH) @PathVariable("model_id") Long modelId
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

    /**
     * GET /v1/ai_models/{model_id} : Get ai_models by model_id
     *
     * @param modelId  (required)
     * @return OK (status code 200)
     *         or Not Found (status code 404)
     *         or Internal Server Error (status code 500)
     * @see AiModelsApi#getAiModels
     */
    @Override
    public AiModels getAiModels(
        @Parameter(name = "model_id", description = "", required = true, in = ParameterIn.PATH) @PathVariable("model_id") Long modelId
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

    /**
     * GET /v1/ai_models : List ai_models
     *
     * @param page  (optional)
     * @param size  (optional)
     * @param sort e.g. created_at,desc (optional)
     * @return OK (status code 200)
     *         or Bad Request (status code 400)
     *         or Internal Server Error (status code 500)
     * @see AiModelsApi#listAiModels
     */
    @Override
    public PageOfAiModels listAiModels(
        @Parameter(name = "page", description = "", in = ParameterIn.QUERY) @RequestParam(value = "page", required = false) Integer page,
        @Parameter(name = "size", description = "", in = ParameterIn.QUERY) @RequestParam(value = "size", required = false) Integer size,
        @Parameter(name = "sort", description = "e.g. created_at,desc", in = ParameterIn.QUERY) @RequestParam(value = "sort", required = false) String sort
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

    /**
     * PUT /v1/ai_models/{model_id} : Update ai_models by model_id
     *
     * @param modelId  (required)
     * @param aiModelsUpdateRequest  (required)
     * @return OK (status code 200)
     *         or Bad Request (status code 400)
     *         or Not Found (status code 404)
     *         or Conflict (status code 409)
     *         or Internal Server Error (status code 500)
     * @see AiModelsApi#updateAiModels
     */
    @Override
    public AiModels updateAiModels(
        @Parameter(name = "model_id", description = "", required = true, in = ParameterIn.PATH) @PathVariable("model_id") Long modelId,
        @Parameter(name = "AiModelsUpdateRequest", description = "", required = true) @RequestBody AiModelsUpdateRequest aiModelsUpdateRequest
    ) {
                throw new IllegalArgumentException("Not implemented");

    }

}
