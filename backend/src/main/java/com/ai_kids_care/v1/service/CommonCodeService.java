package com.ai_kids_care.v1.service;

import com.ai_kids_care.v1.dto.CommonCodeResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CommonCodeService {

    private final JdbcTemplate jdbcTemplate;

    @Transactional(readOnly = true)
    public List<CommonCodeResponse> getActiveCodesByGroup(String group) {
        if (group == null || group.isBlank()) {
            throw new RuntimeException("코드 그룹을 입력해주세요.");
        }

        String codeGroup = group.trim();
        return jdbcTemplate.query(
                """
                SELECT code_group, parent_code, code, code_name, sort_order
                  FROM common_code
                 WHERE UPPER(code_group) = UPPER(?)
                   AND is_active = true
                 ORDER BY sort_order ASC, code ASC
                """,
                (rs, rowNum) -> new CommonCodeResponse(
                        rs.getString("code_group"),
                        rs.getString("parent_code"),
                        rs.getString("code"),
                        rs.getString("code_name"),
                        rs.getInt("sort_order")
                ),
                codeGroup
        );
    }

    @Transactional(readOnly = true)
    public boolean existsActiveCode(String group, String code) {
        if (group == null || group.isBlank() || code == null || code.isBlank()) {
            return false;
        }

        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                  FROM common_code
                 WHERE UPPER(code_group) = UPPER(?)
                   AND UPPER(code) = UPPER(?)
                   AND is_active = true
                """,
                Integer.class,
                group.trim(),
                code.trim()
        );
        return count != null && count > 0;
    }
}
