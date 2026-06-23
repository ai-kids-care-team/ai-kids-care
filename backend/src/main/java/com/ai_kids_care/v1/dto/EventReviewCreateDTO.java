package com.ai_kids_care.v1.dto;

import com.ai_kids_care.v1.type.EventStatusEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 检测事件复核确认请求。reviewer 由会话身份派生(客户端不可指定);kindergarten 由事件派生。
 */
@Getter
@Setter
@Schema(description = "검토(이벤트 리뷰) 확정 요청")
public class EventReviewCreateDTO {

    @NotNull
    @Schema(description = "검토 대상 detection event id", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long eventId;

    @NotNull
    @Schema(description = "검토 결과 상태(ACKNOWLEDGED/IN_REVIEW/RESOLVED/DISMISSED/ESCALATED)",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private EventStatusEnum resultStatus;

    @Schema(description = "선택: 검토 코멘트")
    private String comment;
}
