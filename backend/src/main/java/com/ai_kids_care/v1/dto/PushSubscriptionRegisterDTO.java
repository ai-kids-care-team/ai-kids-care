package com.ai_kids_care.v1.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * 推送订阅自助登记请求。
 *
 * 不含 {@code userId}/{@code status}——服务端从会话身份派生（客户端提交将被忽略：DTO 无对应 field）。
 * {@code provider} 省略时默认 PUSHOVER；仅 PUSHOVER 受支持，其它值由服务端拒为 400。
 */
@Getter
@Setter
@Schema(description = "推送订阅自助登记请求")
public class PushSubscriptionRegisterDTO {

    @Schema(description = "推送提供商；当前仅支持 PUSHOVER（省略默认 PUSHOVER）")
    private String provider;

    @NotBlank
    @Schema(description = "提供商投递地址（Pushover 为 user key）", requiredMode = Schema.RequiredMode.REQUIRED)
    private String address;

    @Schema(description = "可选：设备标签 / Pushover 设备名")
    private String deviceLabel;
}
