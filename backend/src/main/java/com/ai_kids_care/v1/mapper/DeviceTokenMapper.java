package com.ai_kids_care.v1.mapper;

import com.ai_kids_care.v1.dto.DeviceTokenCreateDTO;
import com.ai_kids_care.v1.dto.DeviceTokenUpdateDTO;
import com.ai_kids_care.v1.entity.DeviceToken;
import com.ai_kids_care.v1.vo.DeviceTokenVO;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface DeviceTokenMapper {

    @Mapping(target = "deviceId", ignore = true)
    @Mapping(source = "user.id", target = "userId")
    DeviceTokenVO toVO(DeviceToken entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(source = "userId", target = "user.id")
    @Mapping(target = "createdAt", ignore = true)
    DeviceToken toEntity(DeviceTokenCreateDTO dto);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(source = "userId", target = "user.id")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    void updateEntity(DeviceTokenUpdateDTO dto, @MappingTarget DeviceToken entity);
}