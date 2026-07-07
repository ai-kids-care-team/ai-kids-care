package com.ai_kids_care.v1.dto.internal;

import com.ai_kids_care.v1.vo.internal.ActiveStreamVO;

import java.util.List;

/**
 * Response to {@code POST /api/v1/internal/streams/claim} (shard-live-detection-deployments D2).
 *
 * <p>{@code assigned} is the COMPLETE set of streams this deployment should run this round (the
 * renewed subset of {@code running} union the newly claimed streams) — never a delta. The AI
 * supervisor reconciles its bounded worker pool against this entire set; any locally running worker
 * for a stream id absent from {@code assigned} must be stopped (lease lost / stream no longer active
 * / capacity reduced). Reuses {@link ActiveStreamVO} so this endpoint and {@code GET
 * /internal/streams} stay same-shaped.
 */
public record StreamClaimResponse(List<ActiveStreamVO> assigned) {
}
