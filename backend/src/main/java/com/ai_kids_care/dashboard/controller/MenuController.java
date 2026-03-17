package com.ai_kids_care.dashboard.controller;

import com.ai_kids_care.dashboard.dto.MenuResponse;
import com.ai_kids_care.dashboard.service.MenuService;
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
@RequestMapping("/api/v1/menus")
@RequiredArgsConstructor
@Tag(name = "Menus", description = "Menu APIs")
public class MenuController {

    private final MenuService menuService;

    @GetMapping
    @Operation(summary = "Get menus by role", description = "Returns active menus by role type. ALL menus are included by default.")
    public ResponseEntity<List<MenuResponse>> getMenus(
            @Parameter(description = "Role code (e.g. ALL, TEACHER, KINDERGARTEN_ADMIN, ADMIN)")
            @RequestParam(value = "roleType", required = false) String roleType) {
        return ResponseEntity.ok(menuService.getMenusByRole(roleType));
    }
}
