package com.ai_kids_care.v1.mapper;

import com.ai_kids_care.v1.entity.AuditLog;
import com.ai_kids_care.v1.vo.AuditLogVO;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface AuditLogMapper {

    @Mapping(source = "id", target = "auditId")
    @Mapping(source = "kindergarten.id", target = "kindergartenId")
    @Mapping(source = "user.id", target = "userId")
    AuditLogVO toVO(AuditLog entity);
}
