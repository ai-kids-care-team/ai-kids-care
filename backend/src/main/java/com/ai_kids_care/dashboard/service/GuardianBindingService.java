package com.ai_kids_care.dashboard.service;

import com.ai_kids_care.dashboard.dto.SignupRequest;
import com.ai_kids_care.dashboard.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GuardianBindingService {

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    public void bindGuardianToChild(User user, SignupRequest request) {
        Long childId = request.getChildId();
        String rrnFirst6 = request.getRrnFirst6().trim();
        String rrnBack7 = request.getRrnBack7().trim();
        String rrnEncrypted = passwordEncoder.encode(rrnBack7);
        String guardianGender = deriveGuardianGender(rrnBack7);
        String relationship = resolveRelationship(request);
        boolean isPrimary = Boolean.TRUE.equals(request.getPrimaryGuardian());

        Long kindergartenId = jdbcTemplate.query(
                "SELECT kindergarten_id FROM children WHERE child_id = ?",
                rs -> rs.next() ? rs.getLong("kindergarten_id") : null,
                childId
        );
        if (kindergartenId == null) {
            throw new RuntimeException("선택한 아이 정보를 찾을 수 없습니다.");
        }

        Long guardianId = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(guardian_id), 0) + 1 FROM guardians",
                Long.class
        );
        jdbcTemplate.update(
                """
                INSERT INTO guardians (
                    guardian_id, kindergarten_id, user_id, name,
                    rrn_encrypted, rrn_first6, gender, address,
                    status, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, NULL, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                guardianId, kindergartenId, user.getUserId(), request.getName().trim(),
                rrnEncrypted, rrnFirst6, guardianGender
        );

        jdbcTemplate.update(
                """
                INSERT INTO child_guardian_relationships (
                    kindergarten_id, child_id, guardian_id, relationship,
                    is_primary, priority, start_date, end_date, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, 1, CURRENT_DATE, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                kindergartenId, childId, guardianId, relationship, isPrimary
        );

        Long roleAssignmentId = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(role_assignment_id), 0) + 1 FROM user_role_assignments",
                Long.class
        );
        jdbcTemplate.update(
                """
                INSERT INTO user_role_assignments (
                    role_assignment_id, user_id, role, scope_type, scope_id, status, granted_at, granted_by_user_id, revoked_at
                ) VALUES (?, ?, 'GUARDIAN', 'KINDERGARTEN', ?, 'ACTIVE', CURRENT_TIMESTAMP, NULL, NULL)
                """,
                roleAssignmentId, user.getUserId(), kindergartenId
        );

        Long membershipId = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(membership_id), 0) + 1 FROM user_kindergarten_memberships",
                Long.class
        );
        jdbcTemplate.update(
                """
                INSERT INTO user_kindergarten_memberships (
                    membership_id, user_id, kindergarten_id, status, joined_at, left_at, created_at, updated_at
                ) VALUES (?, ?, ?, 'ACTIVE', CURRENT_TIMESTAMP, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                membershipId, user.getUserId(), kindergartenId
        );
    }

    private String deriveGuardianGender(String rrnBack7) {
        char first = rrnBack7.charAt(0);
        if (first == '1' || first == '3' || first == '5' || first == '7') {
            return "M";
        }
        if (first == '2' || first == '4' || first == '6' || first == '8') {
            return "F";
        }
        throw new RuntimeException("주민등록번호 뒷자리 첫 숫자를 확인해주세요.");
    }

    private String resolveRelationship(SignupRequest request) {
        String relationship = request.getRelationship().trim().toUpperCase();
        if ("OTHER".equals(relationship)) {
            return request.getCustomRelationship().trim();
        }
        return relationship;
    }
}
