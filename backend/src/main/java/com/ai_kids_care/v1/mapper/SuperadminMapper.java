package com.ai_kids_care.v1.mapper;

import com.ai_kids_care.v1.dto.SuperadminCreateDTO;
import com.ai_kids_care.v1.dto.SuperadminUpdateDTO;
import com.ai_kids_care.v1.entity.Superadmin;
import com.ai_kids_care.v1.vo.SuperadminVO;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface SuperadminMapper {

    @Mapping(target = "superadminId", ignore = true)
    @Mapping(source = "user.id", target = "userId")
    SuperadminVO toVO(Superadmin entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(source = "userId", target = "user.id")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Superadmin toEntity(SuperadminCreateDTO dto);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(source = "userId", target = "user.id")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(SuperadminUpdateDTO dto, @MappingTarget Superadmin entity);
}