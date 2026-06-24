package com.ai_kids_care.v1.controller;

import com.ai_kids_care.v1.service.EnumMetadataService;
import com.ai_kids_care.v1.vo.EnumValueVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * ADR-0013 enum 메타데이터 엔드포인트.
 *
 * <p>{@code GET /api/v1/enums/{name}} — 논리 enum 이름으로 백엔드 {@code type.*} enum 의
 * 코드 목록을 반환한다. 퇴역한 {@code common_codes}/{@code menus} 백엔드 딕셔너리를 대체하며
 * 익명 조회가 가능하다(SecurityConfig 화이트리스트). 라벨은 프론트엔드 i18n 책임.
 */
@RestController
@RequestMapping("/api/v1/enums")
@RequiredArgsConstructor
@Tag(name = "Enums", description = "ADR-0013 enum metadata (replaces common_codes dictionary)")
public class EnumsController {

    private final EnumMetadataService enumMetadataService;

    @GetMapping("/{name}")
    @Operation(summary = "Get enum codes by logical name",
            description = "Returns the codes of the backing type.* enum. Labels are resolved by front-end i18n.")
    public ResponseEntity<List<EnumValueVO>> getEnum(
            @Parameter(description = "Logical enum name, e.g. gender, guardian_relationship, teacher_level, status, event_type, event_status")
            @PathVariable String name,
            @Parameter(description = "Optional source table for shared status group; currently passed through")
            @RequestParam(value = "context", required = false) String context
    ) {
        return ResponseEntity.ok(enumMetadataService.getValues(name, context));
    }
}
