package com.ai_kids_care.v1.service;

import com.ai_kids_care.v1.dto.AnnouncementCreateDTO;
import com.ai_kids_care.v1.dto.AnnouncementUpdateDTO;
import com.ai_kids_care.v1.entity.Announcement;
import com.ai_kids_care.v1.mapper.AnnouncementMapper;
import com.ai_kids_care.v1.repository.AnnouncementRepository;
import com.ai_kids_care.v1.vo.AnnouncementMetaVO;
import com.ai_kids_care.v1.vo.AnnouncementSummaryVO;
import com.ai_kids_care.v1.vo.AnnouncementVO;
import com.ai_kids_care.v1.vo.CommonCodeVO;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AnnouncementService {

    private final AnnouncementRepository repository;
    private final AnnouncementMapper mapper;
    private final JdbcTemplate jdbcTemplate;



    private static final RowMapper<AnnouncementSummaryVO> ANNOUNCEMENT_SUMMARY_ROW_MAPPER = (rs, rowNum) ->
            new AnnouncementSummaryVO(
                    rs.getLong("id"),
                    rs.getString("title"),
                    null,
                    rs.getBoolean("is_pinned"),
                    rs.getLong("view_count"),
                    rs.getTimestamp("published_at") == null ? null : rs.getTimestamp("published_at").toInstant(),
                    rs.getTimestamp("created_at").toInstant(),
                    null,
                    null,
                    null,
                    null
            );

    @Transactional(readOnly = true)
    public Page<AnnouncementSummaryVO> listSummaryAnnouncements(String keyword, Pageable pageable) {
        String normalizedKeyword = keyword == null ? null : keyword.trim();
        if (normalizedKeyword != null && normalizedKeyword.isBlank()) {
            normalizedKeyword = null;
        }

        String baseWhere = """
                 WHERE (deleted_at IS NULL OR deleted_at > now())
                   AND status = CAST('ACTIVE' AS status_enum)
                   AND (published_at IS NULL OR published_at <= now())
                   AND (starts_at IS NULL OR starts_at <= now())
                   AND (ends_at IS NULL OR ends_at >= now())
                """;

        String selectCols = """
                SELECT id, title, is_pinned, view_count, published_at, created_at
                  FROM announcements
                """;

        String orderSql = " ORDER BY created_at DESC, id DESC";
        int limit = pageable.getPageSize();
        long offset = pageable.getOffset();

        if (normalizedKeyword == null) {
            String countSql = "SELECT COUNT(*) FROM announcements " + baseWhere;
            Long total = jdbcTemplate.queryForObject(countSql, Long.class);
            long totalLong = total == null ? 0L : total;
            String dataSql = selectCols + baseWhere + orderSql + " LIMIT ? OFFSET ?";
            List<AnnouncementSummaryVO> content = jdbcTemplate.query(
                    dataSql,
                    ANNOUNCEMENT_SUMMARY_ROW_MAPPER,
                    limit,
                    offset
            );
            return new PageImpl<>(content, pageable, totalLong);
        }

        String keywordAnd = """
                   AND (
                        title ILIKE CONCAT('%', ?, '%')
                        OR body ILIKE CONCAT('%', ?, '%')
                   )
                """;
        String countSql = "SELECT COUNT(*) FROM announcements " + baseWhere + keywordAnd;
        Long total = jdbcTemplate.queryForObject(countSql, Long.class, normalizedKeyword, normalizedKeyword);
        long totalLong = total == null ? 0L : total;
        String dataSql = selectCols + baseWhere + keywordAnd + orderSql + " LIMIT ? OFFSET ?";
        List<AnnouncementSummaryVO> content = jdbcTemplate.query(
                dataSql,
                ANNOUNCEMENT_SUMMARY_ROW_MAPPER,
                normalizedKeyword,
                normalizedKeyword,
                limit,
                offset
        );
        return new PageImpl<>(content, pageable, totalLong);
    }


    @Transactional
    public AnnouncementSummaryVO getAnnouncement(Long id) {
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
                SELECT id, title, body, is_pinned, view_count, published_at, created_at,
                       status::text AS status,
                       pinned_until, starts_at, ends_at
                  FROM announcements
                 WHERE id = ?
                   AND deleted_at IS NULL
                """,
                rs -> {
                    if (!rs.next()) {
                        throw new RuntimeException("공지사항을 찾을 수 없습니다.");
                    }
                    return new AnnouncementSummaryVO(
                            rs.getLong("id"),
                            rs.getString("title"),
                            rs.getString("body"),
                            rs.getBoolean("is_pinned"),
                            rs.getLong("view_count"),
                            rs.getTimestamp("published_at") == null ? null : rs.getTimestamp("published_at").toInstant(),
                            rs.getTimestamp("created_at").toInstant(),
                            rs.getString("status"),
                            toInstant(rs, "pinned_until"),
                            toInstant(rs, "starts_at"),
                            toInstant(rs, "ends_at")
                    );
                },
                id
        );
    }

    private static Instant toInstant(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        var ts = rs.getTimestamp(column);
        return ts == null ? null : ts.toInstant();
    }

    private static OffsetDateTime toOffsetDateTime(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        var ts = rs.getTimestamp(column);
        return ts == null ? null : OffsetDateTime.ofInstant(ts.toInstant(), ZoneOffset.UTC);
    }

    @Transactional(readOnly = true)
    public AnnouncementMetaVO getMeta(String loginId) {
        boolean canWrite = hasWritableRole(loginId);
        List<CommonCodeVO> statusOptions = canWrite ? getStatusOptions() : List.of();
        return new AnnouncementMetaVO(canWrite, statusOptions);
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

    private List<CommonCodeVO> getStatusOptions() {
        return jdbcTemplate.query(
                """
                SELECT code_group, parent_code, code, code_name, sort_order
                  FROM common_code
                 WHERE parent_code = 'status'
                   AND code_group = 'announcements'
                   AND is_active = true
                 ORDER BY sort_order ASC, code ASC
                """,
                (rs, rowNum) -> new CommonCodeVO(
                        rs.getString("code_group"),
                        rs.getString("parent_code"),
                        rs.getString("code"),
                        rs.getString("code_name"),
                        rs.getInt("sort_order")
                )
        );
    }

    @Transactional(readOnly = true)
    public AnnouncementVO getAnnouncementForEdit(String loginId, Long id) {
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
                SELECT id,
                       author_id,
                       title,
                       body,
                       is_pinned,
                       pinned_until,
                       status::text AS status,
                       published_at,
                       starts_at,
                       ends_at,
                       view_count,
                       created_at,
                       updated_at,
                       deleted_at
                  FROM announcements
                 WHERE id = ?
                   AND deleted_at IS NULL
                """,
                rs -> {
                    if (!rs.next()) {
                        throw new RuntimeException("공지사항을 찾을 수 없습니다.");
                    }
                    boolean pinnedFlag = rs.getBoolean("is_pinned");
                    return new AnnouncementVO(
                            rs.getLong("id"),
                            rs.getObject("author_id") == null ? null : rs.getLong("author_id"),
                            rs.getString("title"),
                            rs.getString("body"),
                            pinnedFlag,
                            toOffsetDateTime(rs, "pinned_until"),
                            rs.getString("status"),
                            toOffsetDateTime(rs, "published_at"),
                            toOffsetDateTime(rs, "starts_at"),
                            toOffsetDateTime(rs, "ends_at"),
                            rs.getLong("view_count"),
                            toOffsetDateTime(rs, "created_at"),
                            toOffsetDateTime(rs, "updated_at"),
                            toOffsetDateTime(rs, "deleted_at")
                    );
                },
                id
        );
    }

    @Transactional
    public AnnouncementVO createAnnouncement(AnnouncementCreateDTO createDTO, String loginId) {
        if (createDTO.getAuthorId() == null && loginId != null && !loginId.isBlank()) {
            Long userId = findUserIdByLoginId(loginId.trim());
            if (userId != null) {
                createDTO.setAuthorId(userId);
            }
        }
        if (createDTO.getAuthorId() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "작성자 정보를 확인할 수 없습니다. 로그인 후 다시 시도해 주세요."
            );
        }
        if (createDTO.getViewCount() == null) {
            createDTO.setViewCount(0L);
        }
        return mapper.toVO(repository.save(mapper.toEntity(createDTO)));
    }

    private Long findUserIdByLoginId(String loginId) {
        List<Long> ids = jdbcTemplate.queryForList(
                "SELECT user_id FROM users WHERE login_id = ? LIMIT 1",
                Long.class,
                loginId
        );
        return ids.isEmpty() ? null : ids.getFirst();
    }

    public AnnouncementVO updateAnnouncement(Long id, AnnouncementUpdateDTO updateDTO) {
        Announcement entity = repository.findById(id).
                orElseThrow(() -> new EntityNotFoundException("Announcement not found"));
        mapper.updateEntity(updateDTO, entity);
        return mapper.toVO(repository.save(entity));
    }

    public void deleteAnnouncement(Long id) {
        Announcement entity = repository.findById(id).
                orElseThrow(() -> new EntityNotFoundException("Announcement not found"));
        repository.delete(entity);
    }
}
