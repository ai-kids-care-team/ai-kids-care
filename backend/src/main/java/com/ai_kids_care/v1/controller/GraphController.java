package com.ai_kids_care.v1.controller;

import com.ai_kids_care.v1.service.GraphService;
import com.ai_kids_care.v1.vo.graph.ChildGraphVO;
import com.ai_kids_care.v1.vo.graph.TeacherGraphVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only relationship-graph API backed by the Neo4j derived graph. Staff + tenant-scoped: the
 * authorization gate ({@code GRAPH_READ}) and the kindergarten scope are enforced by
 * {@link GraphService} via {@code EffectiveAuthorizationContext} — the caller never supplies
 * {@code kindergartenId}. This controller only routes; it injects the service and nothing else. The
 * endpoints sit under {@code /api/v1/**} (session + CSRF posture), NOT under {@code /api/v1/internal/**}
 * and NOT in any CSRF exemption, and are auto-protected by the default-deny
 * {@code anyRequest().authenticated()} (no SecurityFilterChain change needed).
 */
@Tag(name = "Graph")
@RestController
@RequestMapping("/api/v1/graph")
@RequiredArgsConstructor
public class GraphController {

    private final GraphService graphService;

    @GetMapping("/children/{childId}")
    public ResponseEntity<ChildGraphVO> getChildGraph(@PathVariable Long childId) {
        return ResponseEntity.ok(graphService.getChildGraph(childId));
    }

    @GetMapping("/teachers/{teacherId}")
    public ResponseEntity<TeacherGraphVO> getTeacherGraph(@PathVariable Long teacherId) {
        return ResponseEntity.ok(graphService.getTeacherGraph(teacherId));
    }
}
