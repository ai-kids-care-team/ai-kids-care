package com.ai_kids_care.v1.service;

import com.ai_kids_care.v1.dto.AppreciationLetterCreateDTO;
import com.ai_kids_care.v1.dto.AppreciationLetterUpdateDTO;
import com.ai_kids_care.v1.entity.AppreciationLetter;
import com.ai_kids_care.v1.entity.Kindergarten;
import com.ai_kids_care.v1.entity.User;
import com.ai_kids_care.v1.mapper.AppreciationLetterMapper;
import com.ai_kids_care.v1.repository.AppreciationLetterRepository;
import com.ai_kids_care.v1.repository.GuardianRepository;
import com.ai_kids_care.v1.repository.KindergartenRepository;
import com.ai_kids_care.v1.repository.TeacherRepository;
import com.ai_kids_care.v1.repository.UserRepository;
import com.ai_kids_care.v1.security.EffectiveAuthorizationContext;
import com.ai_kids_care.v1.security.EffectiveAuthorizationContextHolder;
import com.ai_kids_care.v1.security.audit.AuditAction;
import com.ai_kids_care.v1.security.audit.AuditEvent;
import com.ai_kids_care.v1.security.audit.AuditResult;
import com.ai_kids_care.v1.security.audit.SecurityAuditWriter;
import com.ai_kids_care.v1.type.AppreciationTargetTypeEnum;
import com.ai_kids_care.v1.type.StatusEnum;
import com.ai_kids_care.v1.type.UserRoleAssignmentScopeType;
import com.ai_kids_care.v1.type.UserRoleEnum;
import com.ai_kids_care.v1.vo.AppreciationLetterVO;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * SPEC-0003：感谢信读写 Service。
 *
 * 粗粒度门由 {@code @PreAuthorize} 在方法级强制；细粒度可见性（GUARDIAN 自己+公开 /
 * TEACHER 公开+发给本人 / ADMIN 全部）由 Repository JPQL 强制。
 *
 * sender 与 tenant 恒由 {@link EffectiveAuthorizationContext} 派生，客户端提交的
 * senderUserId / kindergartenId / status 字段被 DTO 类结构排除在绑定之外。
 */
@Service
@RequiredArgsConstructor
public class AppreciationLetterService {

    private final AppreciationLetterRepository repository;
    private final AppreciationLetterMapper mapper;
    private final GuardianRepository guardianRepository;
    private final TeacherRepository teacherRepository;
    private final KindergartenRepository kindergartenRepository;
    private final UserRepository userRepository;
    private final SecurityAuditWriter auditWriter;

    // ── 读取（list + get）────────────────────────────────────────────────────────────

    /**
     * GET /api/v1/appreciation_letters —— 按调用角色返回可见集（分页 + keyword）。
     */
    @Transactional(readOnly = true)
    @PreAuthorize("@authorizationPolicy.isAllowed(T(com.ai_kids_care.v1.security.AuthorizationAction).APPRECIATION_LETTER_READ)")
    public Page<AppreciationLetterVO> listAppreciationLetters(String keyword, Pageable pageable) {
        EffectiveAuthorizationContext context = EffectiveAuthorizationContextHolder.require();
        Long kgId = EffectiveAuthorizationContextHolder.requireActiveKindergartenId();
        String kw = normalizeKeyword(keyword);

        return switch (context.role()) {
            case GUARDIAN -> repository
                    .findVisibleForGuardian(kgId, context.userId(), kw, StatusEnum.ACTIVE, pageable)
                    .map(this::buildVO);
            case TEACHER -> {
                Long teacherId = resolveTeacherId(context.userId());
                yield repository
                        .findVisibleForTeacher(kgId, teacherId, kw, StatusEnum.ACTIVE,
                                AppreciationTargetTypeEnum.TEACHER, pageable)
                        .map(this::buildVO);
            }
            case KINDERGARTEN_ADMIN -> repository
                    .findAllForAdmin(kgId, kw, StatusEnum.ACTIVE, pageable)
                    .map(this::buildVO);
            default -> Page.empty(pageable);
        };
    }

    /**
     * GET /api/v1/appreciation_letters/{id} —— 按调用角色检索单封；不可见 → 隐藏 404 + 审计。
     */
    @Transactional(readOnly = true)
    @PreAuthorize("@authorizationPolicy.isAllowed(T(com.ai_kids_care.v1.security.AuthorizationAction).APPRECIATION_LETTER_READ)")
    public AppreciationLetterVO getAppreciationLetter(Long id) {
        EffectiveAuthorizationContext context = EffectiveAuthorizationContextHolder.require();
        Long kgId = EffectiveAuthorizationContextHolder.requireActiveKindergartenId();

        var found = switch (context.role()) {
            case GUARDIAN -> repository.findVisibleForGuardian(id, kgId, context.userId(),
                    StatusEnum.ACTIVE);
            case TEACHER -> {
                Long teacherId = resolveTeacherId(context.userId());
                yield repository.findVisibleForTeacher(id, kgId, teacherId, StatusEnum.ACTIVE,
                        AppreciationTargetTypeEnum.TEACHER);
            }
            case KINDERGARTEN_ADMIN -> repository.findForAdmin(id, kgId, StatusEnum.ACTIVE);
            default -> java.util.Optional.<AppreciationLetter>empty();
        };

        if (found.isEmpty()) {
            recordDenied(context, kgId, id);
            throw new EntityNotFoundException("Appreciation letter not found");
        }
        return buildVO(found.get());
    }

    // ── 写操作（create / update / delete）────────────────────────────────────────────

    /**
     * POST /api/v1/appreciation_letters —— GUARDIAN 创建感谢信。
     */
    @Transactional
    @PreAuthorize("@authorizationPolicy.isAllowed(T(com.ai_kids_care.v1.security.AuthorizationAction).APPRECIATION_LETTER_WRITE)")
    public AppreciationLetterVO createAppreciationLetter(AppreciationLetterCreateDTO dto) {
        EffectiveAuthorizationContext context = EffectiveAuthorizationContextHolder.require();
        Long kgId = EffectiveAuthorizationContextHolder.requireActiveKindergartenId();

        validateTarget(dto.getTargetType(), dto.getTargetId(), kgId);

        AppreciationLetter letter = mapper.toEntity(dto);
        Kindergarten kindergartenRef = kindergartenRepository.getReferenceById(kgId);
        User senderRef = userRepository.getReferenceById(context.userId());
        letter.setKindergarten(kindergartenRef);
        letter.setSenderUser(senderRef);
        letter.setStatus(StatusEnum.ACTIVE);

        repository.save(letter);
        return buildVO(letter);
    }

    /**
     * PUT /api/v1/appreciation_letters/{id} —— GUARDIAN 更新本人信件（patch 语义）。
     */
    @Transactional
    @PreAuthorize("@authorizationPolicy.isAllowed(T(com.ai_kids_care.v1.security.AuthorizationAction).APPRECIATION_LETTER_WRITE)")
    public AppreciationLetterVO updateAppreciationLetter(Long id, AppreciationLetterUpdateDTO dto) {
        EffectiveAuthorizationContext context = EffectiveAuthorizationContextHolder.require();
        Long kgId = EffectiveAuthorizationContextHolder.requireActiveKindergartenId();

        AppreciationLetter letter = repository.findOwnedByAuthor(id, kgId, context.userId(),
                        StatusEnum.ACTIVE)
                .orElseGet(() -> {
                    recordDenied(context, kgId, id);
                    throw new EntityNotFoundException("Appreciation letter not found");
                });

        mapper.updateEntity(dto, letter);
        repository.save(letter);
        return buildVO(letter);
    }

    /**
     * DELETE /api/v1/appreciation_letters/{id} —— GUARDIAN 软删除本人信件（status → DISABLED）。
     */
    @Transactional
    @PreAuthorize("@authorizationPolicy.isAllowed(T(com.ai_kids_care.v1.security.AuthorizationAction).APPRECIATION_LETTER_WRITE)")
    public void deleteAppreciationLetter(Long id) {
        EffectiveAuthorizationContext context = EffectiveAuthorizationContextHolder.require();
        Long kgId = EffectiveAuthorizationContextHolder.requireActiveKindergartenId();

        AppreciationLetter letter = repository.findOwnedByAuthor(id, kgId, context.userId(),
                        StatusEnum.ACTIVE)
                .orElseGet(() -> {
                    recordDenied(context, kgId, id);
                    throw new EntityNotFoundException("Appreciation letter not found");
                });

        letter.setStatus(StatusEnum.DISABLED);
        repository.save(letter);
    }

    // ── 私有工具 ──────────────────────────────────────────────────────────────────────

    /**
     * VO 组装：senderName/targetName 经额外 lookup 解析（Phase 1 N+1；可后续 JOIN 优化）。
     */
    private AppreciationLetterVO buildVO(AppreciationLetter letter) {
        String senderName = guardianRepository
                .findByUser_Id(letter.getSenderUser().getId())
                .map(g -> g.getName())
                .orElse(null);

        String targetName;
        if (letter.getTargetType() == AppreciationTargetTypeEnum.TEACHER) {
            targetName = teacherRepository
                    .findById(letter.getTargetId())
                    .map(t -> t.getName())
                    .orElse(null);
        } else {
            // KINDERGARTEN：取园所名称（已通过 @ManyToOne 关联）
            targetName = letter.getKindergarten().getName();
        }

        return new AppreciationLetterVO(
                letter.getId(),
                senderName,
                letter.getTargetType().name(),
                targetName,
                letter.getTitle(),
                letter.getContent(),
                letter.getIsPublic(),
                letter.getCreatedAt(),
                letter.getUpdatedAt()
        );
    }

    /**
     * 校验 target 与 sender 同租户：TEACHER target 须属于同一 kindergarten；
     * KINDERGARTEN target 的 targetId 须等于当前 kgId。
     */
    private void validateTarget(AppreciationTargetTypeEnum targetType, Long targetId, Long kgId) {
        if (targetType == AppreciationTargetTypeEnum.TEACHER) {
            boolean exists = teacherRepository.findById(targetId)
                    .map(t -> kgId.equals(t.getKindergarten().getId()))
                    .orElse(false);
            if (!exists) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Teacher not found in current kindergarten");
            }
        } else if (targetType == AppreciationTargetTypeEnum.KINDERGARTEN) {
            if (!kgId.equals(targetId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Target kindergarten must be the sender's own kindergarten");
            }
        }
    }

    /**
     * 解析调用者（TEACHER）的 teacher_id（PK），供 target 可见性查询。
     */
    private Long resolveTeacherId(Long userId) {
        return teacherRepository.findByUserId(userId)
                .map(t -> t.getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN, "Teacher profile not found"));
    }

    /**
     * keyword null/blank → null（JPQL `:keyword is null` 分支跳过 LIKE 条件）。
     */
    private String normalizeKeyword(String keyword) {
        return (keyword == null || keyword.isBlank()) ? null : keyword;
    }

    private void recordDenied(EffectiveAuthorizationContext context, Long kgId, Long resourceId) {
        auditWriter.record(AuditEvent.builder()
                .action(AuditAction.AUTHORIZATION_DENIED)
                .result(AuditResult.DENIED)
                .actorUserId(context.userId())
                .scopeType(UserRoleAssignmentScopeType.KINDERGARTEN)
                .kindergartenId(kgId)
                .effectiveRole(context.role().name())
                .resourceType("APPRECIATION_LETTER")
                .resourceId(resourceId)
                .build());
    }
}
