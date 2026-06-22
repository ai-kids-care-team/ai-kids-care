package com.ai_kids_care.v1.mapper;

import com.ai_kids_care.v1.entity.DetectionEvent;
import com.ai_kids_care.v1.vo.DetectionEventVO;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
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
}
