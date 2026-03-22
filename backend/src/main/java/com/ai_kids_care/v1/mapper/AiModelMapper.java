package com.ai_kids_care.v1.mapper;

import com.ai_kids_care.v1.dto.AiModelCreateDTO;
import com.ai_kids_care.v1.dto.AiModelUpdateDTO;
import com.ai_kids_care.v1.entity.AiModel;
import com.ai_kids_care.v1.vo.AiModelVO;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface AiModelMapper {

    @Mapping(target = "modelId", ignore = true)
    AiModelVO toVO(AiModel entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    AiModel toEntity(AiModelCreateDTO dto);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(AiModelUpdateDTO dto, @MappingTarget AiModel entity);
}