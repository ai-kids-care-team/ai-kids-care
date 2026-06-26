package com.ai_kids_care.v1.mapper;

import com.ai_kids_care.v1.entity.CameraStream;
import com.ai_kids_care.v1.vo.CameraStreamVO;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface CameraStreamMapper {

    @Mapping(source = "id", target = "streamId")
    @Mapping(source = "cctvCameras.kindergarten.id", target = "kindergartenId")
    @Mapping(source = "cctvCameras.id", target = "cameraId")
    @Mapping(target = "hasPassword", expression = "java(entity.getStreamPasswordCiphertext() != null && !entity.getStreamPasswordCiphertext().isBlank())")
    CameraStreamVO toVO(CameraStream entity);
}
