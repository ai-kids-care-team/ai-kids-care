package com.ai_kids_care.v1.dto;

import com.ai_kids_care.v1.entity.User;
import com.ai_kids_care.v1.type.UserRoleEnum;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * DTO for {@link User}
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class AuthRegisterRequest implements Serializable {
    @NotNull(message = "로그인 ID를 입력해주세요.")
    @NotBlank(message = "로그인 ID를 입력해주세요.")
    private String loginId;
    @Email
    private String email;
    private String phone;
    @NotNull(message = "비밀번호를 입력해주세요.")
    @NotBlank(message = "비밀번호를 입력해주세요.")
    private String password;
    @NotNull(message = "이름을 입력해주세요.")
    @NotBlank(message = "이름을 입력해주세요.")
    private String name;
    @NotNull(message = "회원유형을 선택해주세요.")
    @NotBlank(message = "회원유형을 선택해주세요.")
    private UserRoleEnum userRole;
    private Long childId;
    private String rrnFirst6;
    private String rrnBack7;
    private String relationship;
    private String customRelationship;
    private Boolean primaryGuardian;
    private String department;
    private Long kindergartenId;
    private String staffNo;
    private String gender;
    private String emergencyContactName;
    private String emergencyContactPhone;
    private String level;
    private String startDate;
    private String endDate;
}