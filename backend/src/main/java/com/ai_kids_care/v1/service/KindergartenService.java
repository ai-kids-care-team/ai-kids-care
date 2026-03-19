package com.ai_kids_care.v1.service;

import com.ai_kids_care.v1.dto.PageOfKindergartens;
import com.ai_kids_care.v1.entity.Kindergarten;
import com.ai_kids_care.v1.repository.KindergartenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class KindergartenService {
    private final KindergartenRepository kindergartenRepository;

    public PageOfKindergartens listKindergartens(String keyword, Integer page, Integer size, String sort) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Kindergarten> kindergartenPage = kindergartenRepository.findByNameContains(keyword, pageable);

        return PageOfKindergartens.builder()
                .items(kindergartenPage.getContent())
                .page(kindergartenPage.getNumber())
                .size(kindergartenPage.getSize())
                .total(kindergartenPage.getTotalElements())
                .build();
    }
}
