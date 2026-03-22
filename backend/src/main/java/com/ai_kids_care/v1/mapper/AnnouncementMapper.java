package com.ai_kids_care.v1.mapper;

import com.ai_kids_care.v1.dto.AnnouncementCreateDTO;
import com.ai_kids_care.v1.dto.AnnouncementUpdateDTO;
import com.ai_kids_care.v1.entity.Announcement;
import com.ai_kids_care.v1.vo.AnnouncementVO;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface AnnouncementMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(source = "author.id", target = "authorId")
    AnnouncementVO toVO(Announcement entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(source = "authorId", target = "author.id")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Announcement toEntity(AnnouncementCreateDTO dto);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(source = "authorId", target = "author.id")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(AnnouncementUpdateDTO dto, @MappingTarget Announcement entity);
}