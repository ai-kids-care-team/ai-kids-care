package com.ai_kids_care.dashboard.dto;

import com.ai_kids_care.dashboard.entity.StatusEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SignupResponse {

    private Long userId;
    private String loginId;
    private StatusEnum status;
}
