package com.ai_kids_care.v1.service;

import com.ai_kids_care.v1.mapper.AuditLogMapper;
import com.ai_kids_care.v1.repository.AuditLogRepository;
import com.ai_kids_care.v1.vo.AuditLogVO;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository repository;
    private final AuditLogMapper mapper;

    @PreAuthorize("denyAll()")
    public Page<AuditLogVO> listAuditLogs(Pageable pageable) {
        return repository.findAll(pageable).map(mapper::toVO);
    }

    @PreAuthorize("denyAll()")
    public AuditLogVO getAuditLog(Long id) {
        return repository.findById(id).map(mapper::toVO)
                .orElseThrow(() -> new EntityNotFoundException("AuditLog not found"));
    }
}
