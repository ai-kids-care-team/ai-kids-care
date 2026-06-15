package com.ai_kids_care.v1.mapper;

import com.ai_kids_care.v1.entity.AppreciationLetter;
import com.ai_kids_care.v1.vo.AppreciationLetterVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AppreciationLetterMapper {

    @Mapping(source = "id", target = "letterId")
    @Mapping(source = "kindergarten.id", target = "kindergartenId")
    @Mapping(source = "senderUser.id", target = "senderUserId")
    AppreciationLetterVO toVO(AppreciationLetter entity);
}
