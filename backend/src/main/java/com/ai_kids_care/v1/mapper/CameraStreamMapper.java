package com.ai_kids_care.v1.mapper;

import com.ai_kids_care.v1.dto.CameraStreamCreateDTO;
import com.ai_kids_care.v1.dto.CameraStreamUpdateDTO;
import com.ai_kids_care.v1.entity.CameraStream;
import com.ai_kids_care.v1.vo.CameraStreamVO;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface CameraStreamMapper {

    @Mapping(source = "id", target = "streamId")
    @Mapping(source = "cctvCameras.kindergarten.id", target = "kindergartenId")
    @Mapping(source = "cctvCameras.id", target = "cameraId")
    @Mapping(target = "hasPassword", expression = "java(entity.getStreamPasswordCiphertext() != null && !entity.getStreamPasswordCiphertext().isBlank())")
    CameraStreamVO toVO(CameraStream entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(source = "cameraId", target = "cctvCameras.id")
    @Mapping(target = "streamPasswordCiphertext", ignore = true)
    @Mapping(target = "streamPasswordIv", ignore = true)
    @Mapping(target = "streamPasswordKeyVersion", ignore = true)
    @Mapping(target = "credentialUpdatedAt", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    CameraStream toEntity(CameraStreamCreateDTO dto);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(source = "cameraId", target = "cctvCameras.id")
    @Mapping(target = "streamPasswordCiphertext", ignore = true)
    @Mapping(target = "streamPasswordIv", ignore = true)
    @Mapping(target = "streamPasswordKeyVersion", ignore = true)
    @Mapping(target = "credentialUpdatedAt", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(CameraStreamUpdateDTO dto, @MappingTarget CameraStream entity);
}