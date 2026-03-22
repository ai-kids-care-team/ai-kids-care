package com.ai_kids_care.v1.service;

import com.ai_kids_care.v1.dto.ClassCreateDTO;
import com.ai_kids_care.v1.dto.ClassUpdateDTO;
import com.ai_kids_care.v1.entity.Class;
import com.ai_kids_care.v1.mapper.ClassMapper;
import com.ai_kids_care.v1.repository.ClassRepository;
import com.ai_kids_care.v1.vo.ClassVO;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ClassService {

    private final ClassRepository repository;
    private final ClassMapper mapper;

    public Page<ClassVO> listClasses(String keyword, Pageable pageable) {
        // TODO: filter Class by keyword
        return repository.findAll(pageable).map(mapper::toVO);
    }

    public ClassVO getClass(Long id) {
        return repository.findById(id).map(mapper::toVO)
                .orElseThrow(() -> new EntityNotFoundException("Class not found"));
    }

    public ClassVO createClass(ClassCreateDTO createDTO) {
        return mapper.toVO(repository.save(mapper.toEntity(createDTO)));
    }

    public ClassVO updateClass(Long id, ClassUpdateDTO updateDTO) {
        Class entity = repository.findById(id).
                orElseThrow(() -> new EntityNotFoundException("Class not found"));
        mapper.updateEntity(updateDTO, entity);
        return mapper.toVO(repository.save(entity));
    }

    public void deleteClass(Long id) {
        Class entity = repository.findById(id).
                orElseThrow(() -> new EntityNotFoundException("Class not found"));
        repository.delete(entity);
    }
}