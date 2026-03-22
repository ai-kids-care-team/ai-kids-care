package com.ai_kids_care.v1.service;

import com.ai_kids_care.v1.dto.GuardianCreateDTO;
import com.ai_kids_care.v1.dto.GuardianUpdateDTO;
import com.ai_kids_care.v1.entity.Guardian;
import com.ai_kids_care.v1.mapper.GuardianMapper;
import com.ai_kids_care.v1.repository.GuardianRepository;
import com.ai_kids_care.v1.vo.GuardianVO;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GuardianService {

    private final GuardianRepository repository;
    private final GuardianMapper mapper;

    public Page<GuardianVO> listGuardians(String keyword, Pageable pageable) {
        // TODO: filter Guardian by keyword
        return repository.findAll(pageable).map(mapper::toVO);
    }

    public GuardianVO getGuardian(Long id) {
        return repository.findById(id).map(mapper::toVO)
                .orElseThrow(() -> new EntityNotFoundException("Guardian not found"));
    }

    public GuardianVO createGuardian(GuardianCreateDTO createDTO) {
        return mapper.toVO(repository.save(mapper.toEntity(createDTO)));
    }

    public GuardianVO updateGuardian(Long id, GuardianUpdateDTO updateDTO) {
        Guardian entity = repository.findById(id).
                orElseThrow(() -> new EntityNotFoundException("Guardian not found"));
        mapper.updateEntity(updateDTO, entity);
        return mapper.toVO(repository.save(entity));
    }

    public void deleteGuardian(Long id) {
        Guardian entity = repository.findById(id).
                orElseThrow(() -> new EntityNotFoundException("Guardian not found"));
        repository.delete(entity);
    }
}