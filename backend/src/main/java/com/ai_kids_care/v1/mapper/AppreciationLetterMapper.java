package com.ai_kids_care.v1.mapper;

import com.ai_kids_care.v1.dto.AppreciationLetterCreateDTO;
import com.ai_kids_care.v1.dto.AppreciationLetterUpdateDTO;
import com.ai_kids_care.v1.entity.AppreciationLetter;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

/**
 * SPEC-0003：感谢信写操作 Mapper。
 *
 * {@code toEntity}：仅映射客户端可控字段（targetType, targetId, title, content, isPublic）；
 * 服务端派生字段（kindergarten, senderUser, status, id, createdAt, updatedAt）由 Service 在
 * 事务内通过显式赋值设置，忽略在此 Mapper 中。
 *
 * {@code updateEntity}：patch 语义——null 字段 = 不变；targetType/targetId/status 不可经客户端更改。
 *
 * 读取侧（toVO）：因 senderName/targetName 需要额外的 Guardian/Teacher lookup，由 Service
 * 的 {@code buildVO()} 私有方法直接组装 VO，不在此 Mapper 中处理。
 */
@Mapper(componentModel = "spring")
public interface AppreciationLetterMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "kindergarten", ignore = true)
    @Mapping(target = "senderUser", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    AppreciationLetter toEntity(AppreciationLetterCreateDTO dto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "kindergarten", ignore = true)
    @Mapping(target = "senderUser", ignore = true)
    @Mapping(target = "targetType", ignore = true)
    @Mapping(target = "targetId", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(AppreciationLetterUpdateDTO dto, @MappingTarget AppreciationLetter entity);
}
