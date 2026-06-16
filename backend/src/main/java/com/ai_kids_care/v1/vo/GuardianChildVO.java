package com.ai_kids_care.v1.vo;

import java.io.Serializable;

/**
 * Guardian 关联儿童的最小读取 VO（SPEC-0001 §3 / §349）。
 *
 * 仅返回业务必要的最小字段（§190：S1 仅对获授权主体返回必要字段）。
 * Guardian 只能读取与自己有 ACTIVE 关系的儿童，故 name 是对获授权主体（本人）的必要展示。
 * 不含 RRN / address / birth_date / childNo / 内部时间戳等（S0/S1 或非必要）。
 */
public record GuardianChildVO(
        Long childId,
        String name,
        String status
) implements Serializable {
}
