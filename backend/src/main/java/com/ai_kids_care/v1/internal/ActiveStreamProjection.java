package com.ai_kids_care.v1.internal;

/**
 * JPQL 构造投影：活跃摄像头流的 {@code (streamId, kindergartenId)}。
 *
 * <p>仅供 {@code CameraStreamRepository#findActiveStreamsForAi()} 的 {@code new ...(...)} 构造表达式
 * 使用——租户归属（{@code kindergartenId}）由后端从 {@code camera_streams → cctv_cameras} 关系在查询内
 * 投影得出（写进 JPQL 谓词，禁「加载后过滤」），{@code modelId} 由 service 另行解析后组装成
 * {@link ActiveStreamVO}。本投影不含任何凭据。
 */
public record ActiveStreamProjection(Long streamId, Long kindergartenId) {
}
