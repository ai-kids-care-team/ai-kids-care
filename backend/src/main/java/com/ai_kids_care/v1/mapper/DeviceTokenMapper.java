package com.ai_kids_care.v1.mapper;

import com.ai_kids_care.v1.entity.DeviceToken;
import com.ai_kids_care.v1.vo.DeviceTokenVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface DeviceTokenMapper {

    @Mapping(source = "id", target = "deviceId")
    @Mapping(source = "user.id", target = "userId")
    DeviceTokenVO toVO(DeviceToken entity);
}
