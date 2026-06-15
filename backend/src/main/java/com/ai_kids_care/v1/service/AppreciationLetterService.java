package com.ai_kids_care.v1.service;

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
}
