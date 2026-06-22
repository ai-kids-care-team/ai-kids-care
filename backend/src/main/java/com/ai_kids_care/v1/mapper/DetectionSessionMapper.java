package com.ai_kids_care.v1.mapper;

import com.ai_kids_care.v1.entity.DetectionSession;
import com.ai_kids_care.v1.vo.DetectionSessionVO;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface DetectionSessionMapper {

    @Mapping(source = "id", target = "sessionId")
    @Mapping(source = "cameraStreams.cctvCameras.kindergarten.id", target = "kindergartenId")
    @Mapping(source = "cameraStreams.cctvCameras.id", target = "cameraId")
    @Mapping(source = "cameraStreams.id", target = "streamId")
    @Mapping(source = "model.id", target = "modelId")
    DetectionSessionVO toVO(DetectionSession entity);
}
