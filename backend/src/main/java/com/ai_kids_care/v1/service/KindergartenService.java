package com.ai_kids_care.v1.service;

import com.ai_kids_care.v1.dto.KindergartenLookupDto;
import com.ai_kids_care.v1.dto.PageOfKindergartens;
import com.ai_kids_care.v1.entity.Kindergarten;
import com.ai_kids_care.v1.repository.KindergartenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class KindergartenService {
    private final KindergartenRepository kindergartenRepository;

    public PageOfKindergartens listKindergartens(String keyword, Integer page, Integer size, String sort) {
        int safePage = page == null || page < 0 ? 0 : page;
        int safeSize = size == null || size <= 0 ? 20 : Math.min(size, 200);
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        String safeKeyword = StringUtils.hasText(keyword) ? keyword.trim() : "";
        Page<Kindergarten> kindergartenPage = kindergartenRepository.findByNameContains(safeKeyword, pageable);

        return PageOfKindergartens.builder()
                .items(kindergartenPage.getContent())
                .page(kindergartenPage.getNumber())
                .size(kindergartenPage.getSize())
                .total(kindergartenPage.getTotalElements())
                .build();
    }

    /**
     * 회원가입용: 사업자등록번호(숫자 10자리)로 유치원 조회.
     *
     * @param raw 하이픈 포함 입력 가능
     */
    public List<KindergartenLookupDto> searchForSignupByBusinessRegistrationNo(String raw) {
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }
        String digits = raw.replaceAll("\\D", "");
        if (digits.length() != 10) {
            return List.of();
        }
        return kindergartenRepository.findByBusinessRegistrationDigits(digits).stream()
                .map(KindergartenLookupDto::fromEntity)
                .toList();
    }
}
