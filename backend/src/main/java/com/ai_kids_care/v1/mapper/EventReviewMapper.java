package com.ai_kids_care.v1.mapper;

import com.ai_kids_care.v1.entity.EventReview;
import com.ai_kids_care.v1.vo.EventReviewVO;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface EventReviewMapper {

    @Mapping(source = "id", target = "reviewId")
    @Mapping(source = "detectionEvents.id", target = "eventId")
    @Mapping(source = "user.id", target = "userId")
    @Mapping(source = "detectionEvents.cctvCameras.kindergarten.id", target = "kindergartenId")
    EventReviewVO toVO(EventReview entity);
}
