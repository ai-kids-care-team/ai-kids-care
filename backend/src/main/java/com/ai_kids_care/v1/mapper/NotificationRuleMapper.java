package com.ai_kids_care.v1.mapper;

import com.ai_kids_care.v1.dto.NotificationRuleCreateDTO;
import com.ai_kids_care.v1.dto.NotificationRuleUpdateDTO;
import com.ai_kids_care.v1.entity.NotificationRule;
import com.ai_kids_care.v1.vo.NotificationRuleVO;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface NotificationRuleMapper {

    @Mapping(target = "ruleId", ignore = true)
    @Mapping(source = "kindergarten.id", target = "kindergartenId")
    @Mapping(source = "user.id", target = "userId")
    NotificationRuleVO toVO(NotificationRule entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(source = "kindergartenId", target = "kindergarten.id")
    @Mapping(source = "userId", target = "user.id")
    @Mapping(target = "createdAt", ignore = true)
    NotificationRule toEntity(NotificationRuleCreateDTO dto);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(source = "kindergartenId", target = "kindergarten.id")
    @Mapping(source = "userId", target = "user.id")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    void updateEntity(NotificationRuleUpdateDTO dto, @MappingTarget NotificationRule entity);
}