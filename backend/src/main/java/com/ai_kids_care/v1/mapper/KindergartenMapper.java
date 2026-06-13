package com.ai_kids_care.v1.mapper;

import com.ai_kids_care.v1.entity.Kindergarten;
import com.ai_kids_care.v1.vo.KindergartenLookupResponse;
import com.ai_kids_care.v1.vo.KindergartenVO;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface KindergartenMapper {

    @Mapping(source = "id", target = "kindergartenId")
    KindergartenVO toVO(Kindergarten entity);

    @Mapping(source = "id", target = "kindergartenId")
    KindergartenLookupResponse toLookupResponse(Kindergarten entity);
}
