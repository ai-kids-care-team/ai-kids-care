package com.ai_kids_care.v1.service;

import com.ai_kids_care.v1.dto.AppreciationLetterCreateDTO;
import com.ai_kids_care.v1.dto.AppreciationLetterUpdateDTO;
import com.ai_kids_care.v1.entity.AppreciationLetter;
import com.ai_kids_care.v1.mapper.AppreciationLetterMapper;
import com.ai_kids_care.v1.repository.AppreciationLetterRepository;
import com.ai_kids_care.v1.vo.AppreciationLetterVO;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AppreciationLetterService {

    private final AppreciationLetterRepository repository;
    private final AppreciationLetterMapper mapper;

    public Page<AppreciationLetterVO> listAppreciationLetters(String keyword, Pageable pageable) {
        // TODO: filter AppreciationLetter by keyword
        return repository.findAll(pageable).map(mapper::toVO);
    }

    public AppreciationLetterVO getAppreciationLetter(Long id) {
        return repository.findById(id).map(mapper::toVO)
                .orElseThrow(() -> new EntityNotFoundException("AppreciationLetter not found"));
    }

    public AppreciationLetterVO createAppreciationLetter(AppreciationLetterCreateDTO createDTO) {
        return mapper.toVO(repository.save(mapper.toEntity(createDTO)));
    }

    public AppreciationLetterVO updateAppreciationLetter(Long id, AppreciationLetterUpdateDTO updateDTO) {
        AppreciationLetter entity = repository.findById(id).
                orElseThrow(() -> new EntityNotFoundException("AppreciationLetter not found"));
        mapper.updateEntity(updateDTO, entity);
        return mapper.toVO(repository.save(entity));
    }

    public void deleteAppreciationLetter(Long id) {
        AppreciationLetter entity = repository.findById(id).
                orElseThrow(() -> new EntityNotFoundException("AppreciationLetter not found"));
        repository.delete(entity);
    }
}