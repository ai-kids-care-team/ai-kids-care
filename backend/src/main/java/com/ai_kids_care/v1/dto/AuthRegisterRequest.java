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
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.Length;

import java.io.Serializable;

/**
 * DTO for {@link User}
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class AuthRegisterRequest implements Serializable {
    @Enumerated(EnumType.STRING)
    @NotNull(message = "회원유형을 선택해주세요.")
    @NotBlank(message = "회원유형을 선택해주세요.")
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

    @NotNull(message = "유치원을 선택해주세요.")
    private Long kindergartenId;

    @NotNull(message = "이름을 입력해주세요.")
    @NotBlank(message = "이름을 입력해주세요.")
    private String name;

    @NotNull(message = "주민등록번호 앞자리를 입력해주세요.")
    @NotBlank(message = "주민등록번호 앞자리를 입력해주세요.")
    @Length(min = 6, max = 6, message = "주민등록번호 앞자리는 숫자 6자리여야 합니다.")
    private String rrnFirst6;

    @NotNull(message = "주민등록번호 뒷자리를 입력해주세요.")
    @NotBlank(message = "주민등록번호 뒷자리를 입력해주세요.")
    @Length(min = 7, max = 7, message = "주민등록번호 뒷자리는 숫자 7자리여야 합니다.")
    private String rrnBack7;

    @Enumerated(EnumType.STRING)
    @NotNull(message = "성별을 입력해주세요.")
    private GenderEnum gender;

    // Guardian
    private String address;

    // Guardian
    @Length(min = 6, max = 6, message = "주민등록번호 앞자리는 숫자 6자리여야 합니다.")
    private String childRrnFirst6;

    // Guardian
    @Length(min = 7, max = 7, message = "주민등록번호 뒷자리는 숫자 7자리여야 합니다.")
    private String childRrnBack7;

    // Guardian
    @Enumerated(EnumType.STRING)
    private RelationshipEnum relationship;

    // Guardian
    private Boolean isPrimaryGuardian;

    // Teacher
    @NotNull(message = "비상 연락처 이름을 입력해주세요.")
    @NotBlank(message = "비상 연락처 이름을 입력해주세요.")
    private String emergencyContactName;

    // Teacher
    @NotNull(message = "비상 연락처 전화번호를 입력해주세요.")
    @NotBlank(message = "비상 연락처 전화번호를 입력해주세요.")
    private String emergencyContactPhone;

    // Teacher
    @Enumerated(EnumType.STRING)
    private LevelEnum level;

    // Superadmin
    private String department;
}