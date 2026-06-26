package com.ai_kids_care.v1.mapper;

import com.ai_kids_care.v1.entity.NotificationRule;
import com.ai_kids_care.v1.vo.NotificationRuleVO;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface NotificationRuleMapper {

    @Mapping(source = "id", target = "ruleId")
    @Mapping(source = "kindergarten.id", target = "kindergartenId")
    @Mapping(source = "user.id", target = "userId")
    NotificationRuleVO toVO(NotificationRule entity);
}
