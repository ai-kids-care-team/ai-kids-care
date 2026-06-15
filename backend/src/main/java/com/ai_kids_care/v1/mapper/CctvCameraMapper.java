package com.ai_kids_care.v1.mapper;

import com.ai_kids_care.v1.entity.CctvCamera;
import com.ai_kids_care.v1.vo.CctvCameraVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CctvCameraMapper {

    @Mapping(source = "id", target = "cameraId")
    @Mapping(source = "kindergarten.id", target = "kindergartenId")
    @Mapping(source = "createdByUser.id", target = "createdByUserId")
    CctvCameraVO toVO(CctvCamera entity);
}
