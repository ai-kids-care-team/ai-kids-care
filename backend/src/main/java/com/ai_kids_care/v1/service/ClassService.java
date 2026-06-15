package com.ai_kids_care.v1.service;

import com.ai_kids_care.v1.dto.ClassCreateDTO;
import com.ai_kids_care.v1.dto.ClassUpdateDTO;
import com.ai_kids_care.v1.entity.Class;
import com.ai_kids_care.v1.mapper.ClassMapper;
import com.ai_kids_care.v1.repository.ClassRepository;
import com.ai_kids_care.v1.repository.KindergartenRepository;
import com.ai_kids_care.v1.security.EffectiveAuthorizationContextHolder;
import com.ai_kids_care.v1.vo.ClassVO;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ClassService {

    private final ClassRepository repository;
    private final KindergartenRepository kindergartenRepository;
    private final ClassMapper mapper;

    @Transactional(readOnly = true)
    @PreAuthorize("@authorizationPolicy.isAllowed(T(com.ai_kids_care.v1.security.AuthorizationAction).TENANT_S2_READ)")
    public Page<ClassVO> listClasses(String keyword, Pageable pageable) {
        Long kindergartenId =
                EffectiveAuthorizationContextHolder.requireActiveKindergartenId();
        return repository.findAllByKindergarten_Id(kindergartenId, pageable)
                .map(mapper::toVO);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@authorizationPolicy.isAllowed(T(com.ai_kids_care.v1.security.AuthorizationAction).TENANT_S2_READ)")
    public ClassVO getClass(Long id) {
        Long kindergartenId =
                EffectiveAuthorizationContextHolder.requireActiveKindergartenId();
        return repository.findByIdAndKindergarten_Id(id, kindergartenId).map(mapper::toVO)
                .orElseThrow(() -> new EntityNotFoundException("Class not found"));
    }

    @Transactional
    @PreAuthorize("@authorizationPolicy.isAllowed(T(com.ai_kids_care.v1.security.AuthorizationAction).TENANT_S2_WRITE)")
    public ClassVO createClass(ClassCreateDTO createDTO) {
        Long kindergartenId =
                EffectiveAuthorizationContextHolder.requireActiveKindergartenId();
        requireSameKindergarten(createDTO.getKindergartenId(), kindergartenId);
        Class entity = mapper.toEntity(createDTO);
        entity.setKindergarten(kindergartenRepository.getReferenceById(kindergartenId));
        return mapper.toVO(repository.save(entity));
    }

    @Transactional
    @PreAuthorize("@authorizationPolicy.isAllowed(T(com.ai_kids_care.v1.security.AuthorizationAction).TENANT_S2_WRITE)")
    public ClassVO updateClass(Long id, ClassUpdateDTO updateDTO) {
        Long kindergartenId =
                EffectiveAuthorizationContextHolder.requireActiveKindergartenId();
        requireSameKindergarten(updateDTO.getKindergartenId(), kindergartenId);
        Class entity = repository.findByIdAndKindergarten_Id(id, kindergartenId)
                .orElseThrow(() -> new EntityNotFoundException("Class not found"));
        mapper.updateEntity(updateDTO, entity);
        return mapper.toVO(repository.save(entity));
    }

    @Transactional
    @PreAuthorize("@authorizationPolicy.isAllowed(T(com.ai_kids_care.v1.security.AuthorizationAction).TENANT_S2_WRITE)")
    public void deleteClass(Long id) {
        Long kindergartenId =
                EffectiveAuthorizationContextHolder.requireActiveKindergartenId();
        Class entity = repository.findByIdAndKindergarten_Id(id, kindergartenId)
                .orElseThrow(() -> new EntityNotFoundException("Class not found"));
        repository.delete(entity);
    }

    private void requireSameKindergarten(Long requested, Long effective) {
        if (requested != null && !requested.equals(effective)) {
            throw new EntityNotFoundException("Class not found");
        }
    }
}
