package com.ai_kids_care.v1.mapper;

import com.ai_kids_care.v1.dto.DetectionEventCreateDTO;
import com.ai_kids_care.v1.dto.DetectionEventUpdateDTO;
import com.ai_kids_care.v1.entity.DetectionEvent;
import com.ai_kids_care.v1.vo.DetectionEventVO;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface DetectionEventMapper {

    @Mapping(source = "id", target = "eventId")
    @Mapping(source = "kindergarten.id", target = "kindergartenId")
    @Mapping(source = "kindergarten.name", target = "kindergartenName")
    @Mapping(source = "cctvCameras.id", target = "cameraId")
    @Mapping(source = "cctvCameras.cameraName", target = "cameraName")
    @Mapping(source = "rooms.id", target = "roomId")
    @Mapping(source = "rooms.name", target = "roomName")
    @Mapping(source = "detectionSessions.id", target = "sessionId")
    DetectionEventVO toVO(DetectionEvent entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(source = "kindergartenId", target = "kindergarten.id")
    @Mapping(source = "cameraId", target = "cctvCameras.id")
    @Mapping(source = "roomId", target = "rooms.id")
    @Mapping(source = "sessionId", target = "detectionSessions.id")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    DetectionEvent toEntity(DetectionEventCreateDTO dto);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(source = "kindergartenId", target = "kindergarten.id")
    @Mapping(source = "cameraId", target = "cctvCameras.id")
    @Mapping(source = "roomId", target = "rooms.id")
    @Mapping(source = "sessionId", target = "detectionSessions.id")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(DetectionEventUpdateDTO dto, @MappingTarget DetectionEvent entity);
}