package com.ai_kids_care.v1.mapper;

import com.ai_kids_care.v1.entity.Teacher;
import com.ai_kids_care.v1.vo.TeacherVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface TeacherMapper {

    @Mapping(target = "teacherId", ignore = true)
    @Mapping(source = "kindergarten.id", target = "kindergartenId")
    @Mapping(source = "user.id", target = "userId")
    TeacherVO toVO(Teacher entity);
}
