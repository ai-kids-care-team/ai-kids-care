package com.ai_kids_care.v1.service;

import com.ai_kids_care.v1.dto.AiModelCreateDTO;
import com.ai_kids_care.v1.dto.AiModelUpdateDTO;
import com.ai_kids_care.v1.entity.AiModel;
import com.ai_kids_care.v1.mapper.AiModelMapper;
import com.ai_kids_care.v1.repository.AiModelRepository;
import com.ai_kids_care.v1.vo.AiModelVO;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AiModelService {

    private final AiModelRepository repository;
    private final AiModelMapper mapper;

    @PreAuthorize("@authorizationPolicy.isAllowed(T(com.ai_kids_care.v1.security.AuthorizationAction).PLATFORM_METADATA_READ)")
    public Page<AiModelVO> listAiModels(String keyword, Pageable pageable) {
        // Matches AiModel.name only — no `description` column exists on ai_models (see
        // AiModelRepository#searchAll javadoc); adding one is a schema change, out of scope.
        // Whitespace-only keywords normalize to null so they short-circuit as "no filter" (mirrors
        // DetectionEventService.listDetectionEvents).
        String normalizedKeyword = StringUtils.hasText(keyword) ? keyword.trim() : null;
        return repository.searchAll(normalizedKeyword, pageable).map(mapper::toVO);
    }

    @PreAuthorize("@authorizationPolicy.isAllowed(T(com.ai_kids_care.v1.security.AuthorizationAction).PLATFORM_METADATA_READ)")
    public AiModelVO getAiModel(Long id) {
        return repository.findById(id).map(mapper::toVO)
                .orElseThrow(() -> new EntityNotFoundException("AiModel not found"));
    }

    @PreAuthorize("@authorizationPolicy.isAllowed(T(com.ai_kids_care.v1.security.AuthorizationAction).PLATFORM_METADATA_WRITE)")
    public AiModelVO createAiModel(AiModelCreateDTO createDTO) {
        return mapper.toVO(repository.save(mapper.toEntity(createDTO)));
    }

    @PreAuthorize("@authorizationPolicy.isAllowed(T(com.ai_kids_care.v1.security.AuthorizationAction).PLATFORM_METADATA_WRITE)")
    public AiModelVO updateAiModel(Long id, AiModelUpdateDTO updateDTO) {
        AiModel entity = repository.findById(id).
                orElseThrow(() -> new EntityNotFoundException("AiModel not found"));
        mapper.updateEntity(updateDTO, entity);
        return mapper.toVO(repository.save(entity));
    }

    @PreAuthorize("@authorizationPolicy.isAllowed(T(com.ai_kids_care.v1.security.AuthorizationAction).PLATFORM_METADATA_WRITE)")
    public void deleteAiModel(Long id) {
        AiModel entity = repository.findById(id).
                orElseThrow(() -> new EntityNotFoundException("AiModel not found"));
        repository.delete(entity);
    }
}
