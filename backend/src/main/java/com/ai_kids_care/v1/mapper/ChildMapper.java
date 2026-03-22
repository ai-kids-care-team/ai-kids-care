package com.ai_kids_care.v1.mapper;

import com.ai_kids_care.v1.dto.ChildCreateDTO;
import com.ai_kids_care.v1.dto.ChildUpdateDTO;
import com.ai_kids_care.v1.entity.Child;
import com.ai_kids_care.v1.vo.ChildVO;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface ChildMapper {

    @Mapping(target = "childId", ignore = true)
    @Mapping(source = "kindergarten.id", target = "kindergartenId")
    ChildVO toVO(Child entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(source = "kindergartenId", target = "kindergarten.id")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Child toEntity(ChildCreateDTO dto);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(source = "kindergartenId", target = "kindergarten.id")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(ChildUpdateDTO dto, @MappingTarget Child entity);
}