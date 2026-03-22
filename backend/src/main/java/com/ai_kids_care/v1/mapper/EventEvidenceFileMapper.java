package com.ai_kids_care.v1.mapper;

import com.ai_kids_care.v1.dto.EventEvidenceFileCreateDTO;
import com.ai_kids_care.v1.dto.EventEvidenceFileUpdateDTO;
import com.ai_kids_care.v1.entity.EventEvidenceFile;
import com.ai_kids_care.v1.vo.EventEvidenceFileVO;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface EventEvidenceFileMapper {

    @Mapping(target = "evidenceId", ignore = true)
    @Mapping(source = "detectionEvents.id", target = "eventId")
    @Mapping(source = "detectionEvents.cctvCameras.kindergarten.id", target = "kindergartenId")
    EventEvidenceFileVO toVO(EventEvidenceFile entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(source = "eventId", target = "detectionEvents.id")
    @Mapping(target = "createdAt", ignore = true)
    EventEvidenceFile toEntity(EventEvidenceFileCreateDTO dto);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(source = "eventId", target = "detectionEvents.id")
    @Mapping(target = "createdAt", ignore = true)
    void updateEntity(EventEvidenceFileUpdateDTO dto, @MappingTarget EventEvidenceFile entity);
}