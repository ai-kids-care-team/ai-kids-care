package com.ai_kids_care.v1.mapper;

import com.ai_kids_care.v1.entity.EventEvidenceFile;
import com.ai_kids_care.v1.vo.EventEvidenceFileVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface EventEvidenceFileMapper {

    @Mapping(target = "evidenceId", ignore = true)
    @Mapping(source = "detectionEvents.id", target = "eventId")
    @Mapping(source = "detectionEvents.cctvCameras.kindergarten.id", target = "kindergartenId")
    EventEvidenceFileVO toVO(EventEvidenceFile entity);
}
