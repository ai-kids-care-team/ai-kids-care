package com.ai_kids_care.v1.service;

import com.ai_kids_care.v1.mapper.TeacherMapper;
import com.ai_kids_care.v1.repository.TeacherRepository;
import com.ai_kids_care.v1.vo.TeacherVO;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TeacherService {

    private final TeacherRepository repository;
    private final TeacherMapper mapper;

    public Page<TeacherVO> listTeachers(String keyword, Long userId, Pageable pageable) {
        boolean hasKeyword = keyword != null && !keyword.isBlank();

        if (hasKeyword && userId != null) {
            return repository.findByNameContainsAndUserId(keyword, userId, pageable).map(mapper::toVO);
        }

        if (hasKeyword) {
            return repository.findByNameContains(keyword, pageable).map(mapper::toVO);
        }

        if (userId != null) {
            return repository.findByUserId(userId, pageable).map(mapper::toVO);
        }

        return repository.findAll(pageable).map(mapper::toVO);
    }

    public TeacherVO getTeacher(Long id) {
        return repository.findById(id).map(mapper::toVO)
                .orElseThrow(() -> new EntityNotFoundException("Teacher not found"));
    }
}
