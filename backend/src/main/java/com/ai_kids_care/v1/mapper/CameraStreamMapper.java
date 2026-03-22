package com.ai_kids_care.v1.mapper;

import com.ai_kids_care.v1.dto.CameraStreamCreateDTO;
import com.ai_kids_care.v1.dto.CameraStreamUpdateDTO;
import com.ai_kids_care.v1.entity.CameraStream;
import com.ai_kids_care.v1.vo.CameraStreamVO;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface CameraStreamMapper {

    @Mapping(target = "streamId", ignore = true)
    @Mapping(source = "cctvCameras.kindergarten.id", target = "kindergartenId")
    @Mapping(source = "cctvCameras.id", target = "cameraId")
    CameraStreamVO toVO(CameraStream entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(source = "cameraId", target = "cctvCameras.id")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    CameraStream toEntity(CameraStreamCreateDTO dto);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(source = "cameraId", target = "cctvCameras.id")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(CameraStreamUpdateDTO dto, @MappingTarget CameraStream entity);
}