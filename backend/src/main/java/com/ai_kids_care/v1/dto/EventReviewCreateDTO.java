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

    @Schema(description = "선택: 공용 공간 이벤트에서 영향받는 아동 id 목록(수동 지정). 비면 교실 관계 그래프 자동 해석")
    private java.util.List<Long> affectedChildIds;

    @Schema(description = "선택: result_status=RESOLVED 시 보호자 통지 여부(ESCALATED는 항상 통지)")
    private Boolean notifyGuardians;
}
