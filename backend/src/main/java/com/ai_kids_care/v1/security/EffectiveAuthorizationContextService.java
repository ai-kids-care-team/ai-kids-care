package com.ai_kids_care.v1.security;

import com.ai_kids_care.v1.entity.Guardian;
import com.ai_kids_care.v1.entity.Kindergarten;
import com.ai_kids_care.v1.entity.Superadmin;
import com.ai_kids_care.v1.entity.Teacher;
import com.ai_kids_care.v1.entity.User;
import com.ai_kids_care.v1.entity.UserKindergartenMembership;
import com.ai_kids_care.v1.entity.UserRoleAssignment;
import com.ai_kids_care.v1.repository.GuardianRepository;
import com.ai_kids_care.v1.repository.KindergartenRepository;
import com.ai_kids_care.v1.repository.SuperadminRepository;
import com.ai_kids_care.v1.repository.TeacherRepository;
import com.ai_kids_care.v1.repository.UserKindergartenMembershipRepository;
import com.ai_kids_care.v1.repository.UserRepository;
import com.ai_kids_care.v1.repository.UserRoleAssignmentRepository;
import com.ai_kids_care.v1.type.StatusEnum;
import com.ai_kids_care.v1.type.UserRoleAssignmentScopeType;
import com.ai_kids_care.v1.type.UserRoleEnum;
import com.ai_kids_care.v1.vo.TenantContextVO;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class EffectiveAuthorizationContextService {

    public static final String SELECTED_KINDERGARTEN_ID =
            EffectiveAuthorizationContextService.class.getName() + ".selectedKindergartenId";

    private final UserRepository userRepository;
    private final UserRoleAssignmentRepository roleAssignmentRepository;
    private final UserKindergartenMembershipRepository membershipRepository;
    private final KindergartenRepository kindergartenRepository;
    private final TeacherRepository teacherRepository;
    private final GuardianRepository guardianRepository;
    private final SuperadminRepository superadminRepository;

    @Transactional(readOnly = true)
    public AuthenticatedSession establishSession(User user) {
        ResolvedIdentity identity = resolveIdentity(user);
        SessionPrincipal principal = new SessionPrincipal(
                user.getId(),
                identity.roleAssignment().getId(),
                identity.membership() == null ? null : identity.membership().getId(),
                OffsetDateTime.now()
        );
        return new AuthenticatedSession(
                principal,
                toContext(user, identity, null).toSessionProfile()
        );
    }

    @Transactional(readOnly = true)
    public EffectiveAuthorizationContext resolve(
            SessionPrincipal principal,
            HttpSession session
    ) {
        User user = userRepository.findById(principal.userId()).orElse(null);
        if (user == null || user.getStatus() != StatusEnum.ACTIVE) {
            throw authenticationFailure();
        }

        ResolvedIdentity identity = resolveIdentity(user);
        Long membershipId = identity.membership() == null ? null : identity.membership().getId();
        if (!identity.roleAssignment().getId().equals(principal.roleAssignmentId())
                || !Objects.equals(membershipId, principal.membershipId())) {
            throw authenticationFailure();
        }

        Long selectedKindergartenId = resolveSelectedKindergarten(session, identity);
        return toContext(user, identity, selectedKindergartenId);
    }

    @Transactional(readOnly = true)
    public TenantContextVO selectTenant(
            EffectiveAuthorizationContext context,
            Long kindergartenId,
            HttpSession session
    ) {
        if (context.scopeType() != UserRoleAssignmentScopeType.PLATFORM) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }
        Kindergarten kindergarten = kindergartenRepository
                .findByIdAndStatus(kindergartenId, StatusEnum.ACTIVE)
                .orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.NOT_FOUND, "Resource not found"));
        session.setAttribute(SELECTED_KINDERGARTEN_ID, kindergarten.getId());
        return new TenantContextVO(kindergarten.getId());
    }

    private ResolvedIdentity resolveIdentity(User user) {
        List<UserRoleAssignment> activeAssignments = roleAssignmentRepository
                .findAllByUser_IdAndStatusOrderByGrantedAtDesc(user.getId(), StatusEnum.ACTIVE);
        if (activeAssignments.size() != 1) {
            throw authenticationFailure();
        }
        UserRoleAssignment roleAssignment = activeAssignments.get(0);
        List<UserKindergartenMembership> activeMemberships = membershipRepository
                .findAllByUser_IdAndStatus(user.getId(), StatusEnum.ACTIVE);
        boolean tenantRole = switch (roleAssignment.getRole()) {
            case GUARDIAN, TEACHER, KINDERGARTEN_ADMIN -> true;
            case PLATFORM_IT_ADMIN, SUPERADMIN -> false;
        };

        if (tenantRole
                && roleAssignment.getScopeType() == UserRoleAssignmentScopeType.KINDERGARTEN
                && roleAssignment.getScopeId() != null
                && activeMemberships.size() == 1
                && roleAssignment.getScopeId().equals(
                        activeMemberships.get(0).getKindergarten().getId())
                && kindergartenRepository.findByIdAndStatus(
                        roleAssignment.getScopeId(),
                        StatusEnum.ACTIVE
                ).isPresent()) {
            return new ResolvedIdentity(roleAssignment, activeMemberships.get(0));
        }
        if (!tenantRole
                && roleAssignment.getScopeType() == UserRoleAssignmentScopeType.PLATFORM
                && roleAssignment.getScopeId() == null
                && activeMemberships.isEmpty()) {
            return new ResolvedIdentity(roleAssignment, null);
        }
        throw authenticationFailure();
    }

    private Long resolveSelectedKindergarten(
            HttpSession session,
            ResolvedIdentity identity
    ) {
        if (session == null
                || identity.roleAssignment().getScopeType()
                != UserRoleAssignmentScopeType.PLATFORM) {
            return null;
        }
        Object value = session.getAttribute(SELECTED_KINDERGARTEN_ID);
        if (!(value instanceof Long selectedKindergartenId)) {
            return null;
        }
        boolean active = kindergartenRepository
                .findByIdAndStatus(selectedKindergartenId, StatusEnum.ACTIVE)
                .isPresent();
        if (!active) {
            session.removeAttribute(SELECTED_KINDERGARTEN_ID);
            return null;
        }
        return selectedKindergartenId;
    }

    private EffectiveAuthorizationContext toContext(
            User user,
            ResolvedIdentity identity,
            Long selectedKindergartenId
    ) {
        UserRoleAssignment roleAssignment = identity.roleAssignment();
        String name = resolveProfileName(user.getId(), roleAssignment.getRole());
        return new EffectiveAuthorizationContext(
                user.getId(),
                user.getLoginId(),
                roleAssignment.getId(),
                roleAssignment.getRole(),
                roleAssignment.getScopeType(),
                roleAssignment.getScopeId(),
                selectedKindergartenId,
                identity.membership() == null ? null : identity.membership().getId(),
                name
        );
    }

    private String resolveProfileName(Long userId, UserRoleEnum role) {
        return switch (role) {
            case TEACHER, KINDERGARTEN_ADMIN ->
                    teacherRepository.findByUserId(userId)
                            .map(Teacher::getName)
                            .orElse(null);
            case GUARDIAN ->
                    guardianRepository.findByUser_Id(userId)
                            .map(Guardian::getName)
                            .orElse(null);
            case SUPERADMIN ->
                    superadminRepository.findByUser_Id(userId)
                            .map(Superadmin::getName)
                            .orElse(null);
            case PLATFORM_IT_ADMIN -> null;
        };
    }

    private ResponseStatusException authenticationFailure() {
        return new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "Authentication failed"
        );
    }

    private record ResolvedIdentity(
            UserRoleAssignment roleAssignment,
            UserKindergartenMembership membership
    ) {
    }
}
