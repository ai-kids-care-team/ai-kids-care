package com.ai_kids_care.v1.service;

import com.ai_kids_care.v1.dto.CommonCodeCreateDTO;
import com.ai_kids_care.v1.dto.CommonCodeUpdateDTO;
import com.ai_kids_care.v1.entity.CommonCode;
import com.ai_kids_care.v1.mapper.CommonCodeMapper;
import com.ai_kids_care.v1.repository.CommonCodeRepository;
import com.ai_kids_care.v1.vo.CommonCodeVO;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CommonCodeService {

    private final CommonCodeRepository repository;
    private final CommonCodeMapper mapper;

    @Transactional(readOnly = true)
    public Page<CommonCodeVO> listCommonCodes(
            String codeGroup,
            String code,
            String parentCode,
            Boolean isActive,
            Pageable pageable
    ) {
        return repository.findAllByFilters(codeGroup, code, parentCode, isActive, pageable)
                .map(mapper::toVO);
    }

    @Transactional(readOnly = true)
    public CommonCodeVO getCommonCode(Long id) {
        return repository.findById(id).map(mapper::toVO)
                .orElseThrow(() -> new EntityNotFoundException("CommonCode not found"));
    }

    @Transactional
    public CommonCodeVO createCommonCode(CommonCodeCreateDTO createDTO) {
        return mapper.toVO(repository.save(mapper.toEntity(createDTO)));
    }

    @Transactional
    public CommonCodeVO updateCommonCode(Long id, CommonCodeUpdateDTO updateDTO) {
        CommonCode entity = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("CommonCode not found"));
        mapper.updateEntity(updateDTO, entity);
        return mapper.toVO(repository.save(entity));
    }

    @Transactional
    public void deleteCommonCode(Long id) {
        CommonCode entity = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("CommonCode not found"));
        repository.delete(entity);
    }
}
