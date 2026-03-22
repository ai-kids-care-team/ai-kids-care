package com.ai_kids_care.v1.mapper;

import com.ai_kids_care.v1.dto.GuardianCreateDTO;
import com.ai_kids_care.v1.dto.GuardianUpdateDTO;
import com.ai_kids_care.v1.entity.Guardian;
import com.ai_kids_care.v1.vo.GuardianVO;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface GuardianMapper {

    @Mapping(target = "guardianId", ignore = true)
    @Mapping(source = "kindergarten.id", target = "kindergartenId")
    @Mapping(source = "user.id", target = "userId")
    GuardianVO toVO(Guardian entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(source = "kindergartenId", target = "kindergarten.id")
    @Mapping(source = "userId", target = "user.id")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Guardian toEntity(GuardianCreateDTO dto);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(source = "kindergartenId", target = "kindergarten.id")
    @Mapping(source = "userId", target = "user.id")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(GuardianUpdateDTO dto, @MappingTarget Guardian entity);
}