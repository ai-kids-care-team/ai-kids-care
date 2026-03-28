package com.ai_kids_care.v1.mapper;

import com.ai_kids_care.v1.dto.AppreciationLetterCreateDTO;
import com.ai_kids_care.v1.dto.AppreciationLetterUpdateDTO;
import com.ai_kids_care.v1.entity.AppreciationLetter;
import com.ai_kids_care.v1.vo.AppreciationLetterVO;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface AppreciationLetterMapper {

    @Mapping(target = "letterId", ignore = true)
    AppreciationLetterVO toVO(AppreciationLetter entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    AppreciationLetter toEntity(AppreciationLetterCreateDTO dto);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(AppreciationLetterUpdateDTO dto, @MappingTarget AppreciationLetter entity);
}