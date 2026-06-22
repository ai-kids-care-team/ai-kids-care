package com.ai_kids_care.v1.mapper;

import com.ai_kids_care.v1.entity.Teacher;
import com.ai_kids_care.v1.vo.TeacherVO;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface TeacherMapper {

    @Mapping(source = "id", target = "teacherId")
    @Mapping(source = "kindergarten.id", target = "kindergartenId")
    @Mapping(source = "user.id", target = "userId")
    TeacherVO toVO(Teacher entity);
}
