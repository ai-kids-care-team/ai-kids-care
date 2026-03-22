package com.ai_kids_care.v1.mapper;

import com.ai_kids_care.v1.dto.TeacherCreateDTO;
import com.ai_kids_care.v1.dto.TeacherUpdateDTO;
import com.ai_kids_care.v1.entity.Teacher;
import com.ai_kids_care.v1.vo.TeacherVO;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface TeacherMapper {

    @Mapping(target = "teacherId", ignore = true)
    @Mapping(source = "kindergarten.id", target = "kindergartenId")
    @Mapping(source = "user.id", target = "userId")
    TeacherVO toVO(Teacher entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(source = "kindergartenId", target = "kindergarten.id")
    @Mapping(source = "userId", target = "user.id")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Teacher toEntity(TeacherCreateDTO dto);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(source = "kindergartenId", target = "kindergarten.id")
    @Mapping(source = "userId", target = "user.id")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(TeacherUpdateDTO dto, @MappingTarget Teacher entity);
}