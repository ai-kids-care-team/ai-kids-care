package com.ai_kids_care.v1.security;

import com.ai_kids_care.v1.repository.TeacherRepository;
import com.ai_kids_care.v1.type.LevelEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;

/**
 * 细粒度资源关系判断（ADR-0019 §2 / §7）。
 *
 * 本 policy 在 @Transactional service 方法内调用，不独立发起事务。
 * 只做断言（assert*）——违反时直接抛 403 / 404，调用方不再重复判断。
 *
 * 粗粒度门（KINDERGARTEN_ADMIN + tenantIdentity）已由 @PreAuthorize 完成；
 * 本类附加：
 *   (a) 调用者 teachers.level ∈ {DIRECTOR, VICE_DIRECTOR}
 *   (b) 目标 userId 所属 kindergarten == context.activeKindergartenId()
 *   (c) context.userId() != targetUserId（禁自审）
 * 条目 (b)(c) 由调用方通过 tenant-aware 查询 + 条件更新保证，
 * 本类提供 level 检查与自审检查的独立断言方法。
 */
@Component
@RequiredArgsConstructor
public class KindergartenAdminPolicy {

    private static final Set<LevelEnum> ELIGIBLE_LEVELS =
            Set.of(LevelEnum.DIRECTOR, LevelEnum.VICE_DIRECTOR);

    private final TeacherRepository teacherRepository;

    /**
     * 断言调用者具备有效的 level（DIRECTOR 或 VICE_DIRECTOR）。
     * 若调用者无 teacher 档案或 level 不符，抛 403。
     */
    public void requireEligibleLevel(EffectiveAuthorizationContext context) {
        teacherRepository.findByUserId(context.userId())
                .filter(t -> ELIGIBLE_LEVELS.contains(t.getLevel()))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Requires DIRECTOR or VICE_DIRECTOR level"));
    }

    /**
     * 断言调用者不是目标用户（禁自审）。
     * 违反时抛 403。
     */
    public void requireNotSelf(EffectiveAuthorizationContext context, Long targetUserId) {
        if (context.userId().equals(targetUserId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Cannot approve or modify your own registration");
        }
    }
}
