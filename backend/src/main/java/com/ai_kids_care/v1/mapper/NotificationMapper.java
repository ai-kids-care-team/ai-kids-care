package com.ai_kids_care.v1.mapper;

import com.ai_kids_care.v1.dto.NotificationCreateDTO;
import com.ai_kids_care.v1.dto.NotificationUpdateDTO;
import com.ai_kids_care.v1.entity.Notification;
import com.ai_kids_care.v1.vo.NotificationVO;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface NotificationMapper {

    @Mapping(target = "notificationId", ignore = true)
    @Mapping(source = "detectionEvents.cctvCameras.kindergarten.id", target = "kindergartenId")
    @Mapping(source = "detectionEvents.id", target = "eventId")
    @Mapping(source = "recipientUser.id", target = "recipientUserId")
    NotificationVO toVO(Notification entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(source = "eventId", target = "detectionEvents.id")
    @Mapping(source = "recipientUserId", target = "recipientUser.id")
    Notification toEntity(NotificationCreateDTO dto);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(source = "eventId", target = "detectionEvents.id")
    @Mapping(source = "recipientUserId", target = "recipientUser.id")
    @Mapping(target = "createdAt", ignore = true)
    void updateEntity(NotificationUpdateDTO dto, @MappingTarget Notification entity);
}