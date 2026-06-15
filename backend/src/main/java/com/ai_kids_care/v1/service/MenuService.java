package com.ai_kids_care.v1.service;

import com.ai_kids_care.v1.vo.MenuVO;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class MenuService {

    private final JdbcTemplate jdbcTemplate;

    @Transactional(readOnly = true)
    public List<MenuVO> getMenusByRole(String roleType) {
        String normalizedRole = roleType == null ? "" : roleType.trim().toUpperCase(Locale.ROOT);

        if (normalizedRole.isBlank() || "ALL".equals(normalizedRole)) {
            return jdbcTemplate.query(
                    """
                    SELECT menu_id, parent_id, menu_name, menu_key, path, icon, role_type, sort_order
                      FROM menu
                     WHERE is_active = TRUE
                       --AND role_type = 'ALL'
                     ORDER BY sort_order, menu_id
                    """,
                    (rs, rowNum) -> new MenuVO(
                            rs.getLong("menu_id"),
                            rs.getObject("parent_id", Long.class),
                            rs.getString("menu_name"),
                            rs.getString("menu_key"),
                            rs.getString("path"),
                            rs.getString("icon"),
                            rs.getString("role_type"),
                            rs.getInt("sort_order")
                    )
            );
        }

        return jdbcTemplate.query(
                """
                SELECT menu_id, parent_id, menu_name, menu_key, path, icon, role_type, sort_order
                  FROM menu
                 WHERE is_active = TRUE
                   AND (role_type = 'ALL' OR role_type = ?)
                 ORDER BY sort_order, menu_id
                """,
                (rs, rowNum) -> new MenuVO(
                        rs.getLong("menu_id"),
                        rs.getObject("parent_id", Long.class),
                        rs.getString("menu_name"),
                        rs.getString("menu_key"),
                        rs.getString("path"),
                        rs.getString("icon"),
                        rs.getString("role_type"),
                        rs.getInt("sort_order")
                ),
                normalizedRole
        );
    }
}
