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

import java.util.List;

@Service
@RequiredArgsConstructor
public class CommonCodeService {

    private final CommonCodeRepository repository;
    private final CommonCodeMapper mapper;


    public Page<CommonCodeVO> listActiveCodesByGroup(String group, Pageable pageable) {
        return repository.findByCodeGroupIgnoreCase(group, pageable).map(mapper::toVO);
    }

    public CommonCodeVO getCommonCode(Long id) {
        return repository.findById(id).map(mapper::toVO).orElseThrow(() -> new EntityNotFoundException("CommonCode not found"));
    }

    public CommonCodeVO createCommonCode(CommonCodeCreateDTO createDTO) {
        return mapper.toVO(repository.save(mapper.toEntity(createDTO)));
    }

    public CommonCodeVO updateCommonCode(Long id, CommonCodeUpdateDTO updateDTO) {
        CommonCode entity = repository.findById(id).orElseThrow(() -> new EntityNotFoundException("CommonCode not found"));
        mapper.updateEntity(updateDTO, entity);
        return mapper.toVO(repository.save(entity));
    }

    public void deleteCommonCode(Long id) {
        CommonCode entity = repository.findById(id).orElseThrow(() -> new EntityNotFoundException("CommonCode not found"));
        repository.delete(entity);
    }

    public List<CommonCodeVO> listCodeGroupCommonCodes(String codeGroup) {
        return mapper.toVOList(repository.findByCodeGroupIgnoreCase(codeGroup));
    }

    public List<CommonCodeVO> listActiveCommonCodes(String codeGroup, String code) {
        return mapper.toVOList(repository.findByCodeGroupIgnoreCaseAndCodeIgnoreCase(codeGroup, code));
    }

}
