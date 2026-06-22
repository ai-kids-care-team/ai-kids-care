package com.ai_kids_care.v1.mapper;

import com.ai_kids_care.v1.entity.PushSubscription;
import com.ai_kids_care.v1.vo.PushSubscriptionVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PushSubscriptionMapper {

    @Mapping(source = "id", target = "pushSubscriptionId")
    @Mapping(source = "user.id", target = "userId")
    PushSubscriptionVO toVO(PushSubscription entity);
}
