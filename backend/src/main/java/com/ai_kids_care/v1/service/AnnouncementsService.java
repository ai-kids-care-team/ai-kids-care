package com.ai_kids_care.v1.service;

import com.ai_kids_care.v1.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AnnouncementsService {

    private final JdbcTemplate jdbcTemplate;
    private final CommonCodeService commonCodeService;

    @Transactional(readOnly = true)
    public List<AnnouncementSummaryResponse> getActiveAnnouncements(String keyword) {
        String normalizedKeyword = keyword == null ? null : keyword.trim();
        if (normalizedKeyword != null && normalizedKeyword.isBlank()) {
            normalizedKeyword = null;
        }

        String baseSql = """
                SELECT id, title, is_pinned, view_count, published_at, created_at
                  FROM announcements
                 WHERE (deleted_at IS NULL OR deleted_at > now())
                   AND status = CAST('ACTIVE' AS status_enum)
                   AND (published_at IS NULL OR published_at <= now())
                   AND (starts_at IS NULL OR starts_at <= now())
                   AND (ends_at IS NULL OR ends_at >= now())
                """;

        String orderSql = " ORDER BY created_at DESC, id DESC";

        if (normalizedKeyword == null) {
            return jdbcTemplate.query(
                    baseSql + orderSql,
                    (rs, rowNum) -> new AnnouncementSummaryResponse(
                            rs.getLong("id"),
                            rs.getString("title"),
                            rs.getBoolean("is_pinned"),
                            rs.getLong("view_count"),
                            rs.getTimestamp("published_at") == null ? null : rs.getTimestamp("published_at").toInstant(),
                            rs.getTimestamp("created_at").toInstant()
                    )
            );
        }

        return jdbcTemplate.query(
                baseSql + """
                   AND (
                        title ILIKE CONCAT('%', ?, '%')
                        OR body ILIKE CONCAT('%', ?, '%')
                   )
                """ + orderSql,
                (rs, rowNum) -> new AnnouncementSummaryResponse(
                        rs.getLong("id"),
                        rs.getString("title"),
                        rs.getBoolean("is_pinned"),
                        rs.getLong("view_count"),
                        rs.getTimestamp("published_at") == null ? null : rs.getTimestamp("published_at").toInstant(),
                        rs.getTimestamp("created_at").toInstant()
                ),
                normalizedKeyword,
                normalizedKeyword
        );
    }

    @Transactional
    public AnnouncementDetailResponse getAnnouncementDetail(Long id) {
        if (id == null) {
            throw new RuntimeException("공지사항 ID가 필요합니다.");
        }

        int updated = jdbcTemplate.update(
                """
                UPDATE announcements
                   SET view_count = view_count + 1
                 WHERE id = ?
                   AND deleted_at IS NULL
                """,
                id
        );
        if (updated == 0) {
            throw new RuntimeException("공지사항을 찾을 수 없습니다.");
        }

        return jdbcTemplate.query(
                """
                SELECT id, title, body, view_count, published_at, created_at
                  FROM announcements
                 WHERE id = ?
                   AND deleted_at IS NULL
                """,
                rs -> {
                    if (!rs.next()) {
                        throw new RuntimeException("공지사항을 찾을 수 없습니다.");
                    }
                    return new AnnouncementDetailResponse(
                            rs.getLong("id"),
                            rs.getString("title"),
                            rs.getString("body"),
                            rs.getLong("view_count"),
                            rs.getTimestamp("published_at") == null ? null : rs.getTimestamp("published_at").toInstant(),
                            rs.getTimestamp("created_at").toInstant()
                    );
                },
                id
        );
    }

    @Transactional(readOnly = true)
    public AnnouncementMetaResponse getMeta(String loginId) {
        boolean canWrite = hasWritableRole(loginId);
        List<AnnouncementStatusOptionResponse> statusOptions = canWrite ? getStatusOptions() : List.of();
        return new AnnouncementMetaResponse(canWrite, statusOptions);
    }

    @Transactional(readOnly = true)
    public AnnouncementEditResponse getAnnouncementForEdit(String loginId, Long id) {
        if (loginId == null || loginId.isBlank()) {
            throw new RuntimeException("로그인이 필요합니다.");
        }
        if (!hasWritableRole(loginId)) {
            throw new RuntimeException("공지사항 수정 권한이 없습니다.");
        }
        if (id == null) {
            throw new RuntimeException("공지사항 ID가 필요합니다.");
        }

        return jdbcTemplate.query(
                """
                SELECT id, title, body, is_pinned, pinned_until, status, published_at, starts_at, ends_at
                  FROM announcements
                 WHERE id = ?
                   AND deleted_at IS NULL
                """,
                rs -> {
                    if (!rs.next()) {
                        throw new RuntimeException("공지사항을 찾을 수 없습니다.");
                    }
                    return new AnnouncementEditResponse(
                            rs.getLong("id"),
                            rs.getString("title"),
                            rs.getString("body"),
                            rs.getBoolean("is_pinned"),
                            rs.getTimestamp("pinned_until") == null ? null : rs.getTimestamp("pinned_until").toInstant(),
                            rs.getString("status"),
                            rs.getTimestamp("published_at") == null ? null : rs.getTimestamp("published_at").toInstant(),
                            rs.getTimestamp("starts_at") == null ? null : rs.getTimestamp("starts_at").toInstant(),
                            rs.getTimestamp("ends_at") == null ? null : rs.getTimestamp("ends_at").toInstant()
                    );
                },
                id
        );
    }

    @Transactional
    public AnnouncementCreateResponse createAnnouncement(String loginId, AnnouncementCreateRequest request) {
        if (loginId == null || loginId.isBlank()) {
            throw new RuntimeException("로그인이 필요합니다.");
        }
        if (!hasWritableRole(loginId)) {
            throw new RuntimeException("공지사항 작성 권한이 없습니다.");
        }
        validateCreateRequest(request);

        Long authorId = findUserIdByLoginId(loginId);
        boolean pinned = Boolean.TRUE.equals(request.getPinned());
        String normalizedStatus = request.getStatus().trim().toUpperCase();
        Instant publishedAt = request.getPublishedAt();
        if (publishedAt == null && "ACTIVE".equals(normalizedStatus)) {
            publishedAt = Instant.now();
        }

        Long createdId = jdbcTemplate.queryForObject(
                """
                INSERT INTO announcements
                    (author_id, title, body, is_pinned, pinned_until, status, published_at, starts_at, ends_at, created_at, updated_at)
                VALUES
                    (?, ?, ?, ?, ?, CAST(? AS status_enum), ?, ?, ?, now(), now())
                RETURNING id
                """,
                Long.class,
                authorId,
                request.getTitle().trim(),
                request.getBody().trim(),
                pinned,
                toTimestamp(request.getPinnedUntil()),
                normalizedStatus,
                toTimestamp(publishedAt),
                toTimestamp(request.getStartsAt()),
                toTimestamp(request.getEndsAt())
        );

        if (createdId == null) {
            throw new RuntimeException("공지사항 저장에 실패했습니다.");
        }
        return new AnnouncementCreateResponse(createdId, "공지사항이 등록되었습니다.");
    }

    @Transactional
    public AnnouncementCreateResponse updateAnnouncement(String loginId, Long id, AnnouncementCreateRequest request) {
        if (loginId == null || loginId.isBlank()) {
            throw new RuntimeException("로그인이 필요합니다.");
        }
        if (!hasWritableRole(loginId)) {
            throw new RuntimeException("공지사항 수정 권한이 없습니다.");
        }
        if (id == null) {
            throw new RuntimeException("공지사항 ID가 필요합니다.");
        }
        validateCreateRequest(request);

        String normalizedStatus = request.getStatus().trim().toUpperCase();
        Instant publishedAt = request.getPublishedAt();
        if (publishedAt == null && "ACTIVE".equals(normalizedStatus)) {
            publishedAt = Instant.now();
        }

        int updated = jdbcTemplate.update(
                """
                UPDATE announcements
                   SET title = ?,
                       body = ?,
                       is_pinned = ?,
                       pinned_until = ?,
                       status = CAST(? AS status_enum),
                       published_at = ?,
                       starts_at = ?,
                       ends_at = ?,
                       updated_at = now()
                 WHERE id = ?
                   AND deleted_at IS NULL
                """,
                request.getTitle().trim(),
                request.getBody().trim(),
                Boolean.TRUE.equals(request.getPinned()),
                toTimestamp(request.getPinnedUntil()),
                normalizedStatus,
                toTimestamp(publishedAt),
                toTimestamp(request.getStartsAt()),
                toTimestamp(request.getEndsAt()),
                id
        );

        if (updated == 0) {
            throw new RuntimeException("공지사항을 찾을 수 없습니다.");
        }

        return new AnnouncementCreateResponse(id, "공지사항이 수정되었습니다.");
    }

    @Transactional
    public AnnouncementCreateResponse deleteAnnouncement(String loginId, Long id) {
        if (loginId == null || loginId.isBlank()) {
            throw new RuntimeException("로그인이 필요합니다.");
        }
        if (!hasWritableRole(loginId)) {
            throw new RuntimeException("공지사항 삭제 권한이 없습니다.");
        }
        if (id == null) {
            throw new RuntimeException("공지사항 ID가 필요합니다.");
        }

        int updated = jdbcTemplate.update(
                """
                UPDATE announcements
                   SET deleted_at = now(),
                       updated_at = now()
                 WHERE id = ?
                   AND deleted_at IS NULL
                """,
                id
        );

        if (updated == 0) {
            throw new RuntimeException("공지사항을 찾을 수 없습니다.");
        }

        return new AnnouncementCreateResponse(id, "공지사항이 삭제되었습니다.");
    }

    private void validateCreateRequest(AnnouncementCreateRequest request) {
        if (request == null) {
            throw new RuntimeException("요청 데이터가 비어 있습니다.");
        }
        if (request.getTitle() == null || request.getTitle().isBlank()) {
            throw new RuntimeException("제목을 입력해주세요.");
        }
        if (request.getBody() == null || request.getBody().isBlank()) {
            throw new RuntimeException("내용을 입력해주세요.");
        }
        if (request.getStatus() == null || request.getStatus().isBlank()) {
            throw new RuntimeException("게시 구분을 선택해주세요.");
        }

        String normalizedStatus = request.getStatus().trim().toUpperCase();
        if (!commonCodeService.existsActiveCode("announcements", normalizedStatus)) {
            throw new RuntimeException("유효하지 않은 게시 구분입니다.");
        }

        Instant startsAt = request.getStartsAt();
        Instant endsAt = request.getEndsAt();
        if (startsAt != null && endsAt != null && startsAt.isAfter(endsAt)) {
            throw new RuntimeException("게시 종료일은 게시 시작일보다 빠를 수 없습니다.");
        }
    }

    private Long findUserIdByLoginId(String loginId) {
        Long userId = jdbcTemplate.queryForObject(
                "SELECT user_id FROM users WHERE login_id = ?",
                Long.class,
                loginId
        );
        if (userId == null) {
            throw new RuntimeException("사용자 정보를 찾을 수 없습니다.");
        }
        return userId;
    }

    private boolean hasWritableRole(String loginId) {
        if (loginId == null || loginId.isBlank()) {
            return false;
        }
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                  FROM user_role_assignments ura
                  JOIN users u ON u.user_id = ura.user_id
                 WHERE u.login_id = ?
                   AND ura.status = CAST('ACTIVE' AS status_enum)
                   AND ura.revoked_at IS NULL
                   AND ura.role IN ('KINDERGARTEN_ADMIN', 'PLATFORM_IT_ADMIN', 'SUPERADMIN')
                """,
                Integer.class,
                loginId
        );
        return count != null && count > 0;
    }

    private List<AnnouncementStatusOptionResponse> getStatusOptions() {
        return jdbcTemplate.query(
                """
                SELECT code, code_name, sort_order
                  FROM common_code
                 WHERE parent_code = 'status'
                   AND code_group = 'announcements'
                   AND is_active = true
                 ORDER BY sort_order ASC, code ASC
                """,
                (rs, rowNum) -> new AnnouncementStatusOptionResponse(
                        rs.getString("code"),
                        rs.getString("code_name"),
                        rs.getInt("sort_order")
                )
        );
    }

    private Timestamp toTimestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }
}
