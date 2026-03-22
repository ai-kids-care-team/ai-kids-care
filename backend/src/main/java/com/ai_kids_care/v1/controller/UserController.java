package com.ai_kids_care.v1.controller;

import com.ai_kids_care.v1.dto.UserCreateDTO;
import com.ai_kids_care.v1.dto.UserUpdateDTO;
import com.ai_kids_care.v1.vo.UserVO;
import com.ai_kids_care.v1.service.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name="User")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService service;

    @GetMapping
    public ResponseEntity<Page<UserVO>> listUser(
            @RequestParam(required = false) String keyword,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(service.listUsers(keyword, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserVO> getUser(@PathVariable Long id) {
        return ResponseEntity.ok(service.getUser(id));
    }

    @PostMapping
    public ResponseEntity<UserVO> createUser(@RequestBody UserCreateDTO createDTO) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createUser(createDTO));
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserVO> updateUser(
            @PathVariable Long id,
            @RequestBody UserUpdateDTO updateDTO
    ) {
        return ResponseEntity.ok(service.updateUser(id, updateDTO));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        service.deleteUser(id);
        return ResponseEntity.noContent().build();
    }
}