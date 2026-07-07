package com.ai_kids_care.v1.dto.internal;

/**
 * 解密后的摄像头流凭据 — ADR-0026 Phase 2 内部凭据接口响应。
 *
 * <p>仅由 {@code /api/v1/internal/streams/{id}/credentials} 返回给经 Bearer token 认证的 AI 服务。
 * 携带明文 {@code streamPassword}，因此：
 * <ul>
 *   <li>绝不经任何公开 VO 暴露（{@code CameraStreamVO} 只出 {@code hasPassword}）；</li>
 *   <li>所属 controller 标注 {@code @Hidden}，不进 OpenAPI / Swagger；</li>
 *   <li>绝不写入访问日志。</li>
 * </ul>
 *
 * <p>位于 {@code com.ai_kids_care.v1.internal}（非 {@code .controller} 包）——刻意避开
 * {@code PublishedOpenApiContractTest} 对「每个 v1 controller operation 必须发布」的强约束，
 * 同时由该 DTO 的 {@code @Hidden} 端点保证不出现在真实 {@code /v3/api-docs}。
 */
public record StreamCredentialDTO(
        Long streamId,
        String sourceUrl,
        String streamUser,
        String streamPassword
) {
}
