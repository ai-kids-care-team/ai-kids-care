package com.ai_kids_care.v1.mapper;

import com.ai_kids_care.v1.entity.Superadmin;
import com.ai_kids_care.v1.vo.SuperadminVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface SuperadminMapper {

    @Mapping(target = "superadminId", ignore = true)
    @Mapping(source = "user.id", target = "userId")
    SuperadminVO toVO(Superadmin entity);
}
