package com.ai_kids_care.v1.service;

import com.ai_kids_care.v1.mapper.GuardianMapper;
import com.ai_kids_care.v1.repository.GuardianRepository;
import com.ai_kids_care.v1.vo.GuardianVO;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GuardianService {

    private final GuardianRepository repository;
    private final GuardianMapper mapper;

    @PreAuthorize("denyAll()")
    public Page<GuardianVO> listGuardians(String keyword, Pageable pageable) {
        // TODO: filter Guardian by keyword
        return repository.findAll(pageable).map(mapper::toVO);
    }

    @PreAuthorize("denyAll()")
    public GuardianVO getGuardian(Long id) {
        return repository.findById(id).map(mapper::toVO)
                .orElseThrow(() -> new EntityNotFoundException("Guardian not found"));
    }
}
