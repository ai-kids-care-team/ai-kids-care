package com.ai_kids_care.v1.controller;

import com.ai_kids_care.v1.dto.KindergartenLookupDto;
import com.ai_kids_care.v1.service.KindergartenService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 회원가입 공개 API — {@code SecurityConfig} 에서 {@code /v1/auth/**} permitAll.
 */
@RestController
@RequestMapping("${openapi.aIKidsCare.base-path:/api}")
@RequiredArgsConstructor
public class AuthKindergartenLookupController {

    private final KindergartenService kindergartenService;

    /**
     * GET /api/v1/auth/kindergartens?businessRegistrationNo=1234567890
     * <p>사업자등록번호 숫자 10자리 기준 조회(하이픈 등 비숫자 무시). 일치하는 행 전부 반환.
     */
    @GetMapping("/v1/auth/kindergartens")
    public List<KindergartenLookupDto> lookupByBusinessRegistrationNo(
            @RequestParam("businessRegistrationNo") String businessRegistrationNo) {
        return kindergartenService.searchForSignupByBusinessRegistrationNo(businessRegistrationNo);
    }
}
