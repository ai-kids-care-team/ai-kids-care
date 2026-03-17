package com.ai_kids_care.dashboard.controller;

import com.ai_kids_care.dashboard.dto.ChildLookupResponse;
import com.ai_kids_care.dashboard.service.ChildLookupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping({"/api/v1/children"})
@RequiredArgsConstructor
@Tag(name = "Children", description = "Child lookup APIs")
public class ChildrenController {

    private final ChildLookupService childLookupService;

    @GetMapping
    @Operation(summary = "Search children by name", description = "Returns up to 50 children matching the given name keyword.")
    public ResponseEntity<List<ChildLookupResponse>> searchChildren(
            @Parameter(description = "Child name keyword", required = true)
            @RequestParam("name") String name) {
        return ResponseEntity.ok(childLookupService.searchChildrenByName(name));
    }
}
