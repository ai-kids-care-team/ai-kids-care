package com.ai_kids_care.v1.mapper;

import com.ai_kids_care.v1.dto.UserUpdateDTO;
import com.ai_kids_care.v1.entity.User;
import com.ai_kids_care.v1.vo.UserVO;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(target = "userId", ignore = true)
    UserVO toVO(User entity);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "passwordHash", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(UserUpdateDTO dto, @MappingTarget User entity);
}
