package com.ai_kids_care.v1.controller;

import com.ai_kids_care.v1.service.GraphService;
import com.ai_kids_care.v1.vo.graph.ChildGraphVO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/graph")
@RequiredArgsConstructor
public class GraphController {

    private final GraphService graphService;

    @GetMapping("/children/{childId}")
    public ResponseEntity<ChildGraphVO> getChildGraph(@PathVariable Long childId) {
        return ResponseEntity.ok(graphService.getChildGraph(childId));
    }
}