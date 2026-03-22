package com.ai_kids_care.v1.mapper;

import com.ai_kids_care.v1.dto.DetectionSessionCreateDTO;
import com.ai_kids_care.v1.dto.DetectionSessionUpdateDTO;
import com.ai_kids_care.v1.entity.DetectionSession;
import com.ai_kids_care.v1.vo.DetectionSessionVO;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface DetectionSessionMapper {

    @Mapping(target = "sessionId", ignore = true)
    @Mapping(source = "cameraStreams.cctvCameras.kindergarten.id", target = "kindergartenId")
    @Mapping(source = "cameraStreams.cctvCameras.id", target = "cameraId")
    @Mapping(source = "cameraStreams.id", target = "streamId")
    @Mapping(source = "model.id", target = "modelId")
    DetectionSessionVO toVO(DetectionSession entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(source = "streamId", target = "cameraStreams.id")
    @Mapping(source = "modelId", target = "model.id")
    DetectionSession toEntity(DetectionSessionCreateDTO dto);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(source = "streamId", target = "cameraStreams.id")
    @Mapping(source = "modelId", target = "model.id")
    void updateEntity(DetectionSessionUpdateDTO dto, @MappingTarget DetectionSession entity);
}