package com.ai_kids_care.v1.service;

import com.ai_kids_care.v1.dto.ChildCreateDTO;
import com.ai_kids_care.v1.dto.ChildUpdateDTO;
import com.ai_kids_care.v1.entity.Child;
import com.ai_kids_care.v1.mapper.ChildMapper;
import com.ai_kids_care.v1.repository.ChildRepository;
import com.ai_kids_care.v1.vo.ChildVO;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChildrenService {

    private final ChildRepository repository;
    private final ChildMapper mapper;

    public Page<ChildVO> listChildren(String keyword, Pageable pageable) {
        // TODO: filter Children by keyword
        return repository.findAll(pageable).map(mapper::toVO);
    }

    public ChildVO getChildren(Long id) {
        return repository.findById(id).map(mapper::toVO)
                .orElseThrow(() -> new EntityNotFoundException("Children not found"));
    }

    public ChildVO createChildren(ChildCreateDTO createDTO) {
        return mapper.toVO(repository.save(mapper.toEntity(createDTO)));
    }

    public ChildVO updateChildren(Long id, ChildUpdateDTO updateDTO) {
        Child entity = repository.findById(id).
                orElseThrow(() -> new EntityNotFoundException("Children not found"));
        mapper.updateEntity(updateDTO, entity);
        return mapper.toVO(repository.save(entity));
    }

    public void deleteChildren(Long id) {
        Child entity = repository.findById(id).
                orElseThrow(() -> new EntityNotFoundException("Children not found"));
        repository.delete(entity);
    }
}