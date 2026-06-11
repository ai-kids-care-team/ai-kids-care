package com.ai_kids_care.v1.service;

import com.ai_kids_care.v1.entity.DeviceToken;
import com.ai_kids_care.v1.mapper.DeviceTokenMapper;
import com.ai_kids_care.v1.repository.DeviceTokenRepository;
import com.ai_kids_care.v1.vo.DeviceTokenVO;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DeviceTokenService {

    private final DeviceTokenRepository repository;
    private final DeviceTokenMapper mapper;

    public Page<DeviceTokenVO> listDeviceTokens(String keyword, Pageable pageable) {
        // TODO: filter DeviceToken by keyword
        return repository.findAll(pageable).map(mapper::toVO);
    }

    public DeviceTokenVO getDeviceToken(Long id) {
        return repository.findById(id).map(mapper::toVO)
                .orElseThrow(() -> new EntityNotFoundException("DeviceToken not found"));
    }

    public void deleteDeviceToken(Long id) {
        DeviceToken entity = repository.findById(id).
                orElseThrow(() -> new EntityNotFoundException("DeviceToken not found"));
        repository.delete(entity);
    }
}
