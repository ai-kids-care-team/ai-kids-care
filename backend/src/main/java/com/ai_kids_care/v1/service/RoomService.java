package com.ai_kids_care.v1.service;

import com.ai_kids_care.v1.dto.RoomCreateDTO;
import com.ai_kids_care.v1.dto.RoomUpdateDTO;
import com.ai_kids_care.v1.entity.Room;
import com.ai_kids_care.v1.mapper.RoomMapper;
import com.ai_kids_care.v1.repository.RoomRepository;
import com.ai_kids_care.v1.vo.RoomVO;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RoomService {

    private final RoomRepository repository;
    private final RoomMapper mapper;

    public Page<RoomVO> listRooms(String keyword, Pageable pageable) {
        // TODO: filter Room by keyword
        return repository.findAll(pageable).map(mapper::toVO);
    }

    public RoomVO getRoom(Long id) {
        return repository.findById(id).map(mapper::toVO)
                .orElseThrow(() -> new EntityNotFoundException("Room not found"));
    }

    public RoomVO createRoom(RoomCreateDTO createDTO) {
        return mapper.toVO(repository.save(mapper.toEntity(createDTO)));
    }

    public RoomVO updateRoom(Long id, RoomUpdateDTO updateDTO) {
        Room entity = repository.findById(id).
                orElseThrow(() -> new EntityNotFoundException("Room not found"));
        mapper.updateEntity(updateDTO, entity);
        return mapper.toVO(repository.save(entity));
    }

    public void deleteRoom(Long id) {
        Room entity = repository.findById(id).
                orElseThrow(() -> new EntityNotFoundException("Room not found"));
        repository.delete(entity);
    }
}