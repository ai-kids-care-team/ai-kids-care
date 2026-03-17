package com.ai_kids_care.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MenuResponse {
    private Long menuId;
    private Long parentId;
    private String menuName;
    private String menuKey;
    private String path;
    private String icon;
    private String roleType;
    private Integer sortOrder;
}
