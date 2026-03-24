package com.ai_kids_care.v1.mapper;

import com.ai_kids_care.v1.dto.CommonCodeCreateDTO;
import com.ai_kids_care.v1.dto.CommonCodeUpdateDTO;
import com.ai_kids_care.v1.entity.CommonCode;
import com.ai_kids_care.v1.vo.CommonCodeVO;
import org.mapstruct.*;

import java.util.List;

@Mapper(componentModel = "spring")
public interface CommonCodeMapper {

    @Mapping(target = "codeId", ignore = true)
    CommonCodeVO toVO(CommonCode entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    CommonCode toEntity(CommonCodeCreateDTO dto);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(CommonCodeUpdateDTO dto, @MappingTarget CommonCode entity);

    List<CommonCodeVO> toVOList(List<CommonCode> entities);
}