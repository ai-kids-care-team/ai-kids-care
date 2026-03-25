package com.ai_kids_care.v1.controller;

import com.ai_kids_care.v1.dto.ChildCreateDTO;
import com.ai_kids_care.v1.dto.ChildUpdateDTO;
import com.ai_kids_care.v1.service.ChildrenService;
import com.ai_kids_care.v1.vo.ChildVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Children")
@RestController
@RequestMapping("/api/v1/children")
@RequiredArgsConstructor
public class ChildrenController {

    private final ChildrenService service;

    @GetMapping
    public ResponseEntity<Page<ChildVO>> listChildren(@RequestParam("keyword") String keyword, @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(service.listChildren(keyword, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ChildVO> getChild(@PathVariable Long id) {
        return ResponseEntity.ok(service.getChild(id));
    }

    @GetMapping("/rrn")
    public ResponseEntity<ChildVO> getChildByRRN(@RequestParam("rrn_First6") String rrn_First6, @RequestParam("rrn_Last7") String rrn_Last7) {
        return ResponseEntity.ok(service.getChildByRRN(rrn_First6, rrn_Last7));
    }

    @PostMapping
    public ResponseEntity<ChildVO> createChildren(@RequestBody ChildCreateDTO createDTO) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createChildren(createDTO));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ChildVO> updateChildren(
            @PathVariable Long id,
            @RequestBody ChildUpdateDTO updateDTO
    ) {
        return ResponseEntity.ok(service.updateChildren(id, updateDTO));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteChildren(@PathVariable Long id) {
        service.deleteChildren(id);
        return ResponseEntity.noContent().build();
    }
}