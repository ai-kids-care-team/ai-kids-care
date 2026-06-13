package com.ai_kids_care.v1.controller;

import com.ai_kids_care.v1.vo.KindergartenLookupResponse;
import com.ai_kids_care.v1.vo.KindergartenVO;
import com.ai_kids_care.v1.service.KindergartenService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Kindergarten")
@RestController
@RequestMapping("/api/v1/kindergartens")
@RequiredArgsConstructor
public class KindergartenController {

    private final KindergartenService service;

    @GetMapping
    public ResponseEntity<Page<KindergartenVO>> listKindergarten(
            @RequestParam(required = false) String keyword,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(service.listKindergartens(keyword, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<KindergartenVO> getKindergarten(@PathVariable Long id) {
        return ResponseEntity.ok(service.getKindergarten(id));
    }

    @GetMapping("/business-registration-no/{businessRegistrationNo}")
    public List<KindergartenLookupResponse> lookupByBusinessRegistrationNo(
            @PathVariable String businessRegistrationNo
    ) {
        return service.searchForSignupByBusinessRegistrationNo(businessRegistrationNo);
    }
}
