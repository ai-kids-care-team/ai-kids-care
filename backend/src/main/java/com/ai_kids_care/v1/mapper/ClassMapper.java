package com.ai_kids_care.v1.mapper;

import com.ai_kids_care.v1.dto.ClassCreateDTO;
import com.ai_kids_care.v1.dto.ClassUpdateDTO;
import com.ai_kids_care.v1.entity.Class;
import com.ai_kids_care.v1.vo.ClassVO;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface ClassMapper {

    @Mapping(target = "classId", ignore = true)
    @Mapping(source = "kindergarten.id", target = "kindergartenId")
    ClassVO toVO(Class entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(source = "kindergartenId", target = "kindergarten.id")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Class toEntity(ClassCreateDTO dto);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(source = "kindergartenId", target = "kindergarten.id")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(ClassUpdateDTO dto, @MappingTarget Class entity);
}