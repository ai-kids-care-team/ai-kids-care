package com.ai_kids_care.v1.service;

import com.ai_kids_care.v1.mapper.SuperadminMapper;
import com.ai_kids_care.v1.repository.SuperadminRepository;
import com.ai_kids_care.v1.vo.SuperadminVO;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SuperadminService {

    private final SuperadminRepository repository;
    private final SuperadminMapper mapper;

    @PreAuthorize("denyAll()")
    public Page<SuperadminVO> listSuperadmins(Pageable pageable) {
        return repository.findAll(pageable).map(mapper::toVO);
    }

    @PreAuthorize("denyAll()")
    public SuperadminVO getSuperadmin(Long id) {
        return repository.findById(id).map(mapper::toVO)
                .orElseThrow(() -> new EntityNotFoundException("Superadmin not found"));
    }
}
