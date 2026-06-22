package com.ai_kids_care.v1.dto;

import com.ai_kids_care.v1.type.AppreciationTargetTypeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * SPEC-0003：感谢信创建请求 DTO。
 *
 * 不含 {@code senderUserId}、{@code kindergartenId}、{@code status}——这些字段由服务端从会话派生，
 * 客户端提交这些字段将被 Jackson 忽略（DTO 类无对应 field）。
 */
@Getter
@Setter
@Schema(description = "感谢信创建请求")
public class AppreciationLetterCreateDTO {

    @NotNull
    @Schema(description = "受信对象类型：KINDERGARTEN 或 TEACHER", requiredMode = Schema.RequiredMode.REQUIRED)
    private AppreciationTargetTypeEnum targetType;

    @NotNull
    @Schema(description = "受信对象 ID（TEACHER 时为 teacher_id；KINDERGARTEN 时为 kindergarten_id）",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private Long targetId;

    @NotBlank
    @Schema(description = "信件标题", requiredMode = Schema.RequiredMode.REQUIRED)
    private String title;

    @NotBlank
    @Schema(description = "信件正文", requiredMode = Schema.RequiredMode.REQUIRED)
    private String content;

    @NotNull
    @Schema(description = "是否对同租户公开", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean isPublic;
}
