package com.ai_kids_care.v1.mapper;

import com.ai_kids_care.v1.dto.NotificationUpdateDTO;
import com.ai_kids_care.v1.entity.Notification;
import com.ai_kids_care.v1.vo.NotificationVO;
import org.mapstruct.*;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface NotificationMapper {

    @Mapping(source = "id", target = "notificationId")
    @Mapping(source = "detectionEvents.cctvCameras.kindergarten.id", target = "kindergartenId")
    @Mapping(source = "detectionEvents.id", target = "eventId")
    @Mapping(source = "recipientUser.id", target = "recipientUserId")
    NotificationVO toVO(Notification entity);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(source = "eventId", target = "detectionEvents.id")
    @Mapping(source = "recipientUserId", target = "recipientUser.id")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "deferredUntil", ignore = true)
    // wire-notification-read-state: read_at is managed exclusively via markRead()'s conditional
    // UPDATE, never via this generic closed write path.
    @Mapping(target = "readAt", ignore = true)
    // kindergarten は closed write パスでは不使用
    @Mapping(target = "kindergarten", ignore = true)
    void updateEntity(NotificationUpdateDTO dto, @MappingTarget Notification entity);
}