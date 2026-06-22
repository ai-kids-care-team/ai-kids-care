package com.ai_kids_care.v1.mapper;

import com.ai_kids_care.v1.entity.Superadmin;
import com.ai_kids_care.v1.vo.SuperadminVO;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface SuperadminMapper {

    @Mapping(source = "id", target = "superadminId")
    @Mapping(source = "user.id", target = "userId")
    SuperadminVO toVO(Superadmin entity);
}
