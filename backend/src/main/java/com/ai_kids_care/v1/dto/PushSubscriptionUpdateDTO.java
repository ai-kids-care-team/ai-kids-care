package com.ai_kids_care.v1.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 推送订阅更新请求（patch 语义：null 字段 = 不变）。
 * 不可经客户端改 {@code userId}/{@code provider}。{@code status} 仅接受 status_enum 标签。
 */
@Getter
@Setter
@Schema(description = "推送订阅更新请求（patch）")
public class PushSubscriptionUpdateDTO {

    @Schema(description = "新的提供商投递地址（Pushover user key）")
    private String address;

    @Schema(description = "设备标签 / Pushover 设备名")
    private String deviceLabel;

    @Schema(description = "订阅状态：ACTIVE / DISABLED 等")
    private String status;
}
