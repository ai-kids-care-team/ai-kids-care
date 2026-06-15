package com.ai_kids_care.v1.service;

import com.ai_kids_care.v1.dto.RoomCreateDTO;
import com.ai_kids_care.v1.dto.RoomUpdateDTO;
import com.ai_kids_care.v1.entity.Room;
import com.ai_kids_care.v1.mapper.RoomMapper;
import com.ai_kids_care.v1.repository.RoomRepository;
import com.ai_kids_care.v1.repository.KindergartenRepository;
import com.ai_kids_care.v1.security.EffectiveAuthorizationContextHolder;
import com.ai_kids_care.v1.vo.RoomVO;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RoomService {

    private final RoomRepository repository;
    private final KindergartenRepository kindergartenRepository;
    private final RoomMapper mapper;

    @Transactional(readOnly = true)
    @PreAuthorize("@authorizationPolicy.isAllowed(T(com.ai_kids_care.v1.security.AuthorizationAction).TENANT_S2_READ)")
    public Page<RoomVO> listRooms(String keyword, Pageable pageable) {
        Long kindergartenId =
                EffectiveAuthorizationContextHolder.requireActiveKindergartenId();
        return repository.findAllByKindergarten_Id(kindergartenId, pageable)
                .map(mapper::toVO);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@authorizationPolicy.isAllowed(T(com.ai_kids_care.v1.security.AuthorizationAction).TENANT_S2_READ)")
    public RoomVO getRoom(Long id) {
        Long kindergartenId =
                EffectiveAuthorizationContextHolder.requireActiveKindergartenId();
        return repository.findByIdAndKindergarten_Id(id, kindergartenId).map(mapper::toVO)
                .orElseThrow(() -> new EntityNotFoundException("Room not found"));
    }

    @Transactional
    @PreAuthorize("@authorizationPolicy.isAllowed(T(com.ai_kids_care.v1.security.AuthorizationAction).TENANT_S2_WRITE)")
    public RoomVO createRoom(RoomCreateDTO createDTO) {
        Long kindergartenId =
                EffectiveAuthorizationContextHolder.requireActiveKindergartenId();
        requireSameKindergarten(createDTO.getKindergartenId(), kindergartenId);
        Room entity = mapper.toEntity(createDTO);
        entity.setKindergarten(kindergartenRepository.getReferenceById(kindergartenId));
        return mapper.toVO(repository.save(entity));
    }

    @Transactional
    @PreAuthorize("@authorizationPolicy.isAllowed(T(com.ai_kids_care.v1.security.AuthorizationAction).TENANT_S2_WRITE)")
    public RoomVO updateRoom(Long id, RoomUpdateDTO updateDTO) {
        Long kindergartenId =
                EffectiveAuthorizationContextHolder.requireActiveKindergartenId();
        requireSameKindergarten(updateDTO.getKindergartenId(), kindergartenId);
        Room entity = repository.findByIdAndKindergarten_Id(id, kindergartenId)
                .orElseThrow(() -> new EntityNotFoundException("Room not found"));
        mapper.updateEntity(updateDTO, entity);
        return mapper.toVO(repository.save(entity));
    }

    @Transactional
    @PreAuthorize("@authorizationPolicy.isAllowed(T(com.ai_kids_care.v1.security.AuthorizationAction).TENANT_S2_WRITE)")
    public void deleteRoom(Long id) {
        Long kindergartenId =
                EffectiveAuthorizationContextHolder.requireActiveKindergartenId();
        Room entity = repository.findByIdAndKindergarten_Id(id, kindergartenId)
                .orElseThrow(() -> new EntityNotFoundException("Room not found"));
        repository.delete(entity);
    }

    private void requireSameKindergarten(Long requested, Long effective) {
        if (requested != null && !requested.equals(effective)) {
            throw new EntityNotFoundException("Room not found");
        }
    }
}
