package com.ai_kids_care.v1.mapper;

import com.ai_kids_care.v1.entity.User;
import com.ai_kids_care.v1.vo.UserVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(target = "userId", ignore = true)
    UserVO toVO(User entity);
}
