package com.ai_kids_care.v1.mapper;

import com.ai_kids_care.v1.entity.User;
import com.ai_kids_care.v1.vo.UserVO;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface UserMapper {

    @Mapping(source = "id", target = "userId")
    UserVO toVO(User entity);
}
