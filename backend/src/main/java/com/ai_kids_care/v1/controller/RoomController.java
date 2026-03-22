package com.ai_kids_care.v1.controller;

import com.ai_kids_care.v1.dto.RoomCreateDTO;
import com.ai_kids_care.v1.dto.RoomUpdateDTO;
import com.ai_kids_care.v1.vo.RoomVO;
import com.ai_kids_care.v1.service.RoomService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name="Room")
@RestController
@RequestMapping("/api/v1/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService service;

    @GetMapping
    public ResponseEntity<Page<RoomVO>> listRoom(
            @RequestParam(required = false) String keyword,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(service.listRooms(keyword, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<RoomVO> getRoom(@PathVariable Long id) {
        return ResponseEntity.ok(service.getRoom(id));
    }

    @PostMapping
    public ResponseEntity<RoomVO> createRoom(@RequestBody RoomCreateDTO createDTO) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createRoom(createDTO));
    }

    @PutMapping("/{id}")
    public ResponseEntity<RoomVO> updateRoom(
            @PathVariable Long id,
            @RequestBody RoomUpdateDTO updateDTO
    ) {
        return ResponseEntity.ok(service.updateRoom(id, updateDTO));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRoom(@PathVariable Long id) {
        service.deleteRoom(id);
        return ResponseEntity.noContent().build();
    }
}