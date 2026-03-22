package com.ai_kids_care.v1.mapper;

import com.ai_kids_care.v1.dto.KindergartenCreateDTO;
import com.ai_kids_care.v1.dto.KindergartenUpdateDTO;
import com.ai_kids_care.v1.entity.Kindergarten;
import com.ai_kids_care.v1.vo.KindergartenVO;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface KindergartenMapper {

    @Mapping(target = "kindergartenId", ignore = true)
    KindergartenVO toVO(Kindergarten entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Kindergarten toEntity(KindergartenCreateDTO dto);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(KindergartenUpdateDTO dto, @MappingTarget Kindergarten entity);
}