package com.ai_kids_care.v1.controller;

import com.ai_kids_care.v1.dto.ChildCreateDTO;
import com.ai_kids_care.v1.dto.ChildUpdateDTO;
import com.ai_kids_care.v1.service.AuthService;
import com.ai_kids_care.v1.vo.ChildVO;
import com.ai_kids_care.v1.service.ChildrenService;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Tag(name="Children")
@RestController
@RequestMapping("/api/v1/children")
@RequiredArgsConstructor
public class ChildrenController {

    private final ChildrenService service;
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    @GetMapping
    public ResponseEntity<Page<ChildVO>> listChildren(

            @RequestParam("keyword") String keyword,
//            @ParameterObject @PageableDefault(size = 20) Pageable pageable
                    @Parameter(name = "page", description = "", in = ParameterIn.QUERY) @RequestParam(value = "page", required = false) Integer page,
            @Parameter(name = "size", description = "", in = ParameterIn.QUERY) @RequestParam(value = "size", required = false) Integer size,
            @Parameter(name = "sort", description = "e.g. created_at,desc", in = ParameterIn.QUERY) @RequestParam(value = "sort", required = false) String sort
    ) {
        return ResponseEntity.ok(service.listChildren(keyword, page, size, sort));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ChildVO> getChildren(@PathVariable Long id) {
        return ResponseEntity.ok(service.getChildren(id));
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