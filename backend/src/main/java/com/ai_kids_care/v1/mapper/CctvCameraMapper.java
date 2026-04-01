package com.ai_kids_care.v1.mapper;

import com.ai_kids_care.v1.dto.CctvCameraCreateDTO;
import com.ai_kids_care.v1.dto.CctvCameraUpdateDTO;
import com.ai_kids_care.v1.entity.CctvCamera;
import com.ai_kids_care.v1.vo.CctvCameraVO;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface CctvCameraMapper {

    @Mapping(source = "id", target = "cameraId")
    @Mapping(source = "kindergarten.id", target = "kindergartenId")
    @Mapping(source = "createdByUser.id", target = "createdByUserId")
    CctvCameraVO toVO(CctvCamera entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(source = "kindergartenId", target = "kindergarten.id")
    @Mapping(source = "createdByUserId", target = "createdByUser.id")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    CctvCamera toEntity(CctvCameraCreateDTO dto);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(source = "kindergartenId", target = "kindergarten.id")
    @Mapping(source = "createdByUserId", target = "createdByUser.id")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(CctvCameraUpdateDTO dto, @MappingTarget CctvCamera entity);
}