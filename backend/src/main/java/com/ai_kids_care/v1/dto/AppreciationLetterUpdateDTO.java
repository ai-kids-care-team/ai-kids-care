package com.ai_kids_care.v1.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * SPEC-0003：感谢信更新请求 DTO（patch 语义；null 字段 = 不变）。
 *
 * 不含 {@code targetType}、{@code targetId}（目标不可变）；不含 {@code senderUserId}、
 * {@code kindergartenId}、{@code status}（服务端管理）。
 */
@Getter
@Setter
@Schema(description = "感谢信更新请求（patch 语义；null 字段不变）")
public class AppreciationLetterUpdateDTO {

    @Schema(description = "新标题（null = 不变）")
    private String title;

    @Schema(description = "新正文（null = 不变）")
    private String content;

    @Schema(description = "是否公开（null = 不变）")
    private Boolean isPublic;
}
