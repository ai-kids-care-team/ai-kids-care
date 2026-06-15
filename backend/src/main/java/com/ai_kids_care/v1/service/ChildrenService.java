package com.ai_kids_care.v1.service;

import com.ai_kids_care.v1.entity.Child;
import com.ai_kids_care.v1.mapper.ChildMapper;
import com.ai_kids_care.v1.repository.ChildRepository;
import com.ai_kids_care.v1.vo.ChildVO;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ChildrenService {

    private final ChildRepository repository;
    private final ChildMapper mapper;
    private final PasswordEncoder passwordEncoder;

    public Page<ChildVO> listChildren(String keyword, Pageable pageable) {
        return repository.findByNameContains(keyword, pageable).map(mapper::toVO);
    }

    public ChildVO getChild(Long id) {
        return repository.findById(id).map(mapper::toVO).orElseThrow(() -> new EntityNotFoundException("Children not found"));
    }

    Optional<Child> getChildEntityByRRN(String rrn_First6, String rrn_Last7) {
        return repository.findByRrnFirst6(rrn_First6).stream()
                .filter(child -> passwordEncoder.matches(rrn_Last7, child.getRrnEncrypted()))
                .findFirst();
    }
}
