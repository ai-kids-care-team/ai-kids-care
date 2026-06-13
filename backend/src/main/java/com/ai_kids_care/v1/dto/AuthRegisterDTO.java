package com.ai_kids_care.v1.dto;

import com.ai_kids_care.v1.entity.User;
import com.ai_kids_care.v1.type.GenderEnum;
import com.ai_kids_care.v1.type.LevelEnum;
import com.ai_kids_care.v1.type.RelationshipEnum;
import com.ai_kids_care.v1.type.UserRoleEnum;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.io.Serializable;

/**
 * DTO for {@link User}
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AuthRegisterDTO implements Serializable {
    @Enumerated(EnumType.STRING)
    @NotNull(message = "회원유형을 선택해주세요.")
    private UserRoleEnum userRole;

    @NotNull(message = "로그인 ID를 입력해주세요.")
    @NotBlank(message = "로그인 ID를 입력해주세요.")
    private String loginId;

    @Email
    @NotNull(message = "Email주소를 입력해주세요.")
    @NotBlank(message = "Email주소를 입력해주세요.")
    private String email;

    @NotNull(message = "전화번호를 입력해주세요.")
    @NotBlank(message = "전화번호를 입력해주세요.")
    private String phone;

    @NotNull(message = "비밀번호를 입력해주세요.")
    @NotBlank(message = "비밀번호를 입력해주세요.")
    private String password;

    /** TEACHER/KINDERGARTEN_ADMIN only; Guardian scope is derived from the matched child. */
    private Long kindergartenId;

    @NotNull(message = "이름을 입력해주세요.")
    @NotBlank(message = "이름을 입력해주세요.")
    private String name;

    /** Guardian/Teacher/KINDERGARTEN_ADMIN only; role-specific presence is checked by AuthService. */
    @Pattern(regexp = "\\d{6}", message = "주민등록번호 앞자리는 숫자 6자리여야 합니다.")
    private String rrnFirst6;

    @Pattern(regexp = "\\d{7}", message = "주민등록번호 뒷자리는 숫자 7자리여야 합니다.")
    private String rrnBack7;

    @Enumerated(EnumType.STRING)
    private GenderEnum gender;

    // Guardian
    private String address;

    // Guardian
    @Pattern(regexp = "\\d{6}", message = "주민등록번호 앞자리는 숫자 6자리여야 합니다.")
    private String childRrnFirst6;

    // Guardian
    @Pattern(regexp = "\\d{7}", message = "주민등록번호 뒷자리는 숫자 7자리여야 합니다.")
    private String childRrnBack7;

    /** 프론트 공통코드: MOTHER, FATHER, MATERNAL_GRANDMOTHER 등 → 서버에서 DB 허용 값으로 매핑 */
    // Guardian
    @Enumerated(EnumType.STRING)
    private RelationshipEnum relationship;

    // Guardian
    private Boolean primaryGuardian;

    // Teacher
    private String emergencyContactName;

    // Teacher
    private String emergencyContactPhone;

    // Teacher
    @Enumerated(EnumType.STRING)
    private LevelEnum level;
    private String staffNo;

    // Superadmin
    private String department;

}
