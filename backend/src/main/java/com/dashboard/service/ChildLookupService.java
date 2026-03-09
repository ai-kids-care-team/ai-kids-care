package com.dashboard.service;

import com.dashboard.dto.ChildLookupResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ChildLookupService {

    private final JdbcTemplate jdbcTemplate;

    @Transactional(readOnly = true)
    public List<ChildLookupResponse> searchChildrenByName(String name) {
        if (name == null || name.isBlank()) {
            return List.of();
        }

        String keyword = name.trim();
        return jdbcTemplate.query(
                """
                SELECT c.child_id,
                       c.kindergarten_id,
                       c.class_id,
                       ce.name AS class_name,
                       c.name,
                       c.child_no,
                       c.birth_date,
                       c.gender
                  FROM child c
             LEFT JOIN class_entity ce
                    ON ce.class_id = c.class_id
                   AND ce.kindergarten_id = c.kindergarten_id
                 WHERE c.name ILIKE '%' || ? || '%'
                 ORDER BY c.child_id
                 LIMIT 50
                """,
                (rs, rowNum) -> new ChildLookupResponse(
                        rs.getLong("child_id"),
                        rs.getLong("kindergarten_id"),
                        getNullableLong(rs, "class_id"),
                        rs.getString("class_name"),
                        rs.getString("name"),
                        rs.getString("child_no"),
                        rs.getDate("birth_date") != null ? rs.getDate("birth_date").toLocalDate() : null,
                        rs.getString("gender")
                ),
                keyword
        );
    }

    private Long getNullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }
}
