package com.ai_kids_care.v1.mapper;

import com.ai_kids_care.v1.entity.EventEvidenceFile;
import com.ai_kids_care.v1.vo.EventEvidenceFileVO;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface EventEvidenceFileMapper {

    @Mapping(source = "id", target = "evidenceId")
    @Mapping(source = "detectionEvents.id", target = "eventId")
    // DetectionEvent has a direct kindergarten association (event_id -> kindergarten_id); use that
    // instead of the longer detectionEvents.cctvCameras.kindergarten.id detour.
    @Mapping(source = "detectionEvents.kindergarten.id", target = "kindergartenId")
    // D-STORE: MimeTypeEnum carries the wire MIME literal via @JsonValue-annotated getValue()
    // (e.g. "image/jpeg"); MapStruct's default enum->String mapping uses name() ("IMAGE_JPEG"),
    // which does not match the API contract's `mimeType` field. Map it explicitly.
    @Mapping(target = "mimeType", expression = "java(entity.getMimeType() != null ? entity.getMimeType().getValue() : null)")
    // D-STORE: contentPath/available are computed by EventEvidenceFileService (need
    // EvidenceStoragePort + the mapped record's own evidenceId) — not derivable from the entity
    // alone. The service reconstructs the final record with both fields filled in.
    @Mapping(target = "contentPath", ignore = true)
    @Mapping(target = "available", ignore = true)
    EventEvidenceFileVO toVO(EventEvidenceFile entity);
}
