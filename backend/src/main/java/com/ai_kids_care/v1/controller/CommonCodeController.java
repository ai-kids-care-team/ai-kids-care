package com.ai_kids_care.v1.controller;

import com.ai_kids_care.v1.dto.CommonCodeCreateDTO;
import com.ai_kids_care.v1.dto.CommonCodeUpdateDTO;
import com.ai_kids_care.v1.vo.CommonCodeVO;
import com.ai_kids_care.v1.service.CommonCodeService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "CommonCode")
@RestController
@RequestMapping("/api/v1/common_codes")
@RequiredArgsConstructor
public class CommonCodeController {

    private final CommonCodeService service;

    @GetMapping
    public ResponseEntity<Page<CommonCodeVO>> listCommonCode(@RequestParam(required = false) String keyword, @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(service.listActiveCodesByGroup(keyword, pageable));
    }

    @GetMapping("/code_group/{codeGroup}")
    public ResponseEntity<List<CommonCodeVO>> listCodeGroupCommonCodes(@PathVariable String codeGroup){
        return ResponseEntity.ok(service.listCodeGroupCommonCodes(codeGroup));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CommonCodeVO> getCommonCode(@PathVariable Long id) {
        return ResponseEntity.ok(service.getCommonCode(id));
    }

    @PostMapping
    public ResponseEntity<CommonCodeVO> createCommonCode(@RequestBody CommonCodeCreateDTO createDTO) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createCommonCode(createDTO));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CommonCodeVO> updateCommonCode(@PathVariable Long id, @RequestBody CommonCodeUpdateDTO updateDTO) {
        return ResponseEntity.ok(service.updateCommonCode(id, updateDTO));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCommonCode(@PathVariable Long id) {
        service.deleteCommonCode(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/code_group/{codeGroup}/code/{code}")
    public ResponseEntity<List<CommonCodeVO>> listAllCommonCodes(@PathVariable String codeGroup, @PathVariable String code) {
        return ResponseEntity.ok(service.listActiveCommonCodes(codeGroup, code));
    }
}