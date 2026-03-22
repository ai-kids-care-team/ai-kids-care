package com.ai_kids_care.v1.mapper;

import com.ai_kids_care.v1.dto.AuditLogCreateDTO;
import com.ai_kids_care.v1.dto.AuditLogUpdateDTO;
import com.ai_kids_care.v1.entity.AuditLog;
import com.ai_kids_care.v1.vo.AuditLogVO;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface AuditLogMapper {

    @Mapping(target = "auditId", ignore = true)
    @Mapping(source = "kindergarten.id", target = "kindergartenId")
    @Mapping(source = "user.id", target = "userId")
    AuditLogVO toVO(AuditLog entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(source = "kindergartenId", target = "kindergarten.id")
    @Mapping(source = "userId", target = "user.id")
    @Mapping(target = "createdAt", ignore = true)
    AuditLog toEntity(AuditLogCreateDTO dto);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(source = "userId", target = "user.id")
    @Mapping(source = "kindergartenId", target = "kindergarten.id")
    @Mapping(target = "createdAt", ignore = true)
    void updateEntity(AuditLogUpdateDTO dto, @MappingTarget AuditLog entity);
}