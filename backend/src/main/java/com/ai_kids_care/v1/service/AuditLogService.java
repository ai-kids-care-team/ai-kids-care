package com.ai_kids_care.v1.service;

import com.ai_kids_care.v1.dto.AuditLogCreateDTO;
import com.ai_kids_care.v1.dto.AuditLogUpdateDTO;
import com.ai_kids_care.v1.entity.AuditLog;
import com.ai_kids_care.v1.mapper.AuditLogMapper;
import com.ai_kids_care.v1.repository.AuditLogRepository;
import com.ai_kids_care.v1.vo.AuditLogVO;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository repository;
    private final AuditLogMapper mapper;

    public Page<AuditLogVO> listAuditLogs(String keyword, Pageable pageable) {
        // TODO: filter AuditLog by keyword
        return repository.findAll(pageable).map(mapper::toVO);
    }

    public AuditLogVO getAuditLog(Long id) {
        return repository.findById(id).map(mapper::toVO)
                .orElseThrow(() -> new EntityNotFoundException("AuditLog not found"));
    }

    public AuditLogVO createAuditLog(AuditLogCreateDTO createDTO) {
        return mapper.toVO(repository.save(mapper.toEntity(createDTO)));
    }

    public AuditLogVO updateAuditLog(Long id, AuditLogUpdateDTO updateDTO) {
        AuditLog entity = repository.findById(id).
                orElseThrow(() -> new EntityNotFoundException("AuditLog not found"));
        mapper.updateEntity(updateDTO, entity);
        return mapper.toVO(repository.save(entity));
    }

    public void deleteAuditLog(Long id) {
        AuditLog entity = repository.findById(id).
                orElseThrow(() -> new EntityNotFoundException("AuditLog not found"));
        repository.delete(entity);
    }
}