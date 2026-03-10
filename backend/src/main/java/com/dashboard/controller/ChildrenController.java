package com.dashboard.controller;

import com.dashboard.dto.ChildLookupResponse;
import com.dashboard.service.ChildLookupService;
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
public class ChildrenController {

    private final ChildLookupService childLookupService;

    @GetMapping
    public ResponseEntity<List<ChildLookupResponse>> searchChildren(@RequestParam("name") String name) {
        return ResponseEntity.ok(childLookupService.searchChildrenByName(name));
    }
}
