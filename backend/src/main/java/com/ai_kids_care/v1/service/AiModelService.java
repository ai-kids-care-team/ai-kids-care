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
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AiModelService {

    private final AiModelRepository repository;
    private final AiModelMapper mapper;

    public Page<AiModelVO> listAiModels(String keyword, Pageable pageable) {
        // TODO: filter AiModel by keyword
        return repository.findAll(pageable).map(mapper::toVO);
    }

    public AiModelVO getAiModel(Long id) {
        return repository.findById(id).map(mapper::toVO)
                .orElseThrow(() -> new EntityNotFoundException("AiModel not found"));
    }

    public AiModelVO createAiModel(AiModelCreateDTO createDTO) {
        return mapper.toVO(repository.save(mapper.toEntity(createDTO)));
    }

    public AiModelVO updateAiModel(Long id, AiModelUpdateDTO updateDTO) {
        AiModel entity = repository.findById(id).
                orElseThrow(() -> new EntityNotFoundException("AiModel not found"));
        mapper.updateEntity(updateDTO, entity);
        return mapper.toVO(repository.save(entity));
    }

    public void deleteAiModel(Long id) {
        AiModel entity = repository.findById(id).
                orElseThrow(() -> new EntityNotFoundException("AiModel not found"));
        repository.delete(entity);
    }
}