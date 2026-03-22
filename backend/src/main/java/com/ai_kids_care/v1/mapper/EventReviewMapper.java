package com.ai_kids_care.v1.mapper;

import com.ai_kids_care.v1.dto.EventReviewCreateDTO;
import com.ai_kids_care.v1.dto.EventReviewUpdateDTO;
import com.ai_kids_care.v1.entity.EventReview;
import com.ai_kids_care.v1.vo.EventReviewVO;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface EventReviewMapper {

    @Mapping(target = "reviewId", ignore = true)
    @Mapping(source = "detectionEvents.id", target = "eventId")
    @Mapping(source = "user.id", target = "userId")
    @Mapping(source = "detectionEvents.cctvCameras.kindergarten.id", target = "kindergartenId")
    EventReviewVO toVO(EventReview entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(source = "eventId", target = "detectionEvents.id")
    @Mapping(source = "userId", target = "user.id")
    @Mapping(target = "createdAt", ignore = true)
    EventReview toEntity(EventReviewCreateDTO dto);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(source = "eventId", target = "detectionEvents.id")
    @Mapping(source = "userId", target = "user.id")
    @Mapping(target = "createdAt", ignore = true)
    void updateEntity(EventReviewUpdateDTO dto, @MappingTarget EventReview entity);
}