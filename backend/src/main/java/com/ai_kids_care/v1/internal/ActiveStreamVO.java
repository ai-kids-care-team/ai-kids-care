package com.ai_kids_care.v1.internal;

/**
 * 内部「活跃流清单」响应元素 — 多摄像头 supervisor 枚举本部署应消费的活跃流（方案 A）。
 *
 * <p>由 {@code GET /api/v1/internal/streams} 返回给经 Bearer token 认证的 AI 服务（{@code ROLE_AI_SERVICE}）。
 * 仅携带 {@code streamId} / {@code modelId} / {@code kindergartenId}，**绝不含解密凭据**——凭据仍走既有
 * {@code GET /api/v1/internal/streams/{id}/credentials}。租户归属（{@code kindergartenId}）由后端从
 * {@code camera_streams} 关系在查询内推导（JPQL 谓词，非加载后过滤）；AI 不直连 PostgreSQL。
 *
 * <p>位于 {@code com.ai_kids_care.v1.internal}（非 {@code .controller} / {@code .vo} 包）——刻意避开
 * {@code PublishedOpenApiContractTest} 的「每个 v1 controller operation 必须发布」强约束，且其所属端点
 * 标注 {@code @Hidden} 不进 {@code /v3/api-docs}。
 */
public record ActiveStreamVO(Long streamId, Long modelId, Long kindergartenId) {
}
