package com.ai_kids_care.v1.service;

import com.ai_kids_care.v1.mapper.DetectionEventMapper;
import com.ai_kids_care.v1.repository.DetectionEventRepository;
import com.ai_kids_care.v1.vo.DetectionEventVO;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DetectionEventService {

    private final DetectionEventRepository repository;
    private final DetectionEventMapper mapper;

    // 此 Service 尚未接入任何 live Controller。
    // 默认拒绝所有调用，防止将来误用绕过授权。
    // 接入时须先通过 SPEC 定义 AuthorizationAction 并替换此注解。
    @PreAuthorize("denyAll()")
    @Transactional(readOnly = true)
    public Page<DetectionEventVO> listDetectionEvents(Long kindergartenId, String keyword, Pageable pageable) {
        // TODO: filter DetectionEvent by keyword
        return repository.findByKindergarten_Id(kindergartenId, pageable).map(mapper::toVO);
    }

    // 此 Service 尚未接入任何 live Controller。
    // 默认拒绝所有调用，防止将来误用绕过授权。
    // 接入时须先通过 SPEC 定义 AuthorizationAction 并替换此注解。
    @PreAuthorize("denyAll()")
    @Transactional(readOnly = true)
    public DetectionEventVO getDetectionEvent(Long id, Long kindergartenId) {
        return repository.findByIdAndKindergarten_Id(id, kindergartenId).map(mapper::toVO)
                .orElseThrow(() -> new EntityNotFoundException("DetectionEvent not found"));
    }
}
