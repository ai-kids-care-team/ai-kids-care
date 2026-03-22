package com.ai_kids_care.v1.service;

import com.ai_kids_care.v1.dto.SuperadminCreateDTO;
import com.ai_kids_care.v1.dto.SuperadminUpdateDTO;
import com.ai_kids_care.v1.entity.Superadmin;
import com.ai_kids_care.v1.mapper.SuperadminMapper;
import com.ai_kids_care.v1.repository.SuperadminRepository;
import com.ai_kids_care.v1.vo.SuperadminVO;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SuperadminService {

    private final SuperadminRepository repository;
    private final SuperadminMapper mapper;

    public Page<SuperadminVO> listSuperadmins(String keyword, Pageable pageable) {
        // TODO: filter Superadmin by keyword
        return repository.findAll(pageable).map(mapper::toVO);
    }

    public SuperadminVO getSuperadmin(Long id) {
        return repository.findById(id).map(mapper::toVO)
                .orElseThrow(() -> new EntityNotFoundException("Superadmin not found"));
    }

    public SuperadminVO createSuperadmin(SuperadminCreateDTO createDTO) {
        return mapper.toVO(repository.save(mapper.toEntity(createDTO)));
    }

    public SuperadminVO updateSuperadmin(Long id, SuperadminUpdateDTO updateDTO) {
        Superadmin entity = repository.findById(id).
                orElseThrow(() -> new EntityNotFoundException("Superadmin not found"));
        mapper.updateEntity(updateDTO, entity);
        return mapper.toVO(repository.save(entity));
    }

    public void deleteSuperadmin(Long id) {
        Superadmin entity = repository.findById(id).
                orElseThrow(() -> new EntityNotFoundException("Superadmin not found"));
        repository.delete(entity);
    }
}