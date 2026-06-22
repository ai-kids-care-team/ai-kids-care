package com.ai_kids_care.v1.mapper;

import com.ai_kids_care.v1.entity.Child;
import com.ai_kids_care.v1.vo.ChildVO;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ChildMapper {

    @Mapping(source = "id", target = "childId")
    @Mapping(source = "kindergarten.id", target = "kindergartenId")
    ChildVO toVO(Child entity);
}
