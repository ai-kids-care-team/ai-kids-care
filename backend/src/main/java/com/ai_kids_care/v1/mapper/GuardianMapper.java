package com.ai_kids_care.v1.mapper;

import com.ai_kids_care.v1.entity.Guardian;
import com.ai_kids_care.v1.vo.GuardianVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface GuardianMapper {

    @Mapping(target = "guardianId", ignore = true)
    @Mapping(source = "kindergarten.id", target = "kindergartenId")
    @Mapping(source = "user.id", target = "userId")
    GuardianVO toVO(Guardian entity);
}
