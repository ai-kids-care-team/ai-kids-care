package com.ai_kids_care.v1.service;

import com.ai_kids_care.v1.dto.PageOfChildren;
import com.ai_kids_care.v1.entity.Child;
import com.ai_kids_care.v1.repository.ChildRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChildService {
    private final ChildRepository childRepository;

    public Page<Child> listChildren(String keyword, Integer page, Integer size, String sort) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return childRepository.findByNameContains(keyword, pageable);
    }
}
