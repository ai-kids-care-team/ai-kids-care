package com.ai_kids_care.v1.dto;

import com.ai_kids_care.v1.entity.User;
import com.ai_kids_care.v1.type.GenderEnum;
import com.ai_kids_care.v1.type.UserRoleEnum;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.Length;

import java.io.Serializable;

/**
 * DTO for {@link User} registration.
 * <p>
 * 서버에서 사용자·역할·관계 테이블 status 는 ACTIVE 로 고정합니다.
 * 프론트에서 {@code status: ACTIVE} 를내도 무시됩니다.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AuthRegisterRequest implements Serializable {

    @NotNull(message = "회원유형을 선택해주세요.")
    private UserRoleEnum userRole;

    @NotBlank(message = "로그인 ID를 입력해주세요.")
    private String loginId;

    @Email
    @NotBlank(message = "Email주소를 입력해주세요.")
    private String email;

    @NotBlank(message = "전화번호를 입력해주세요.")
    private String phone;

    @NotBlank(message = "비밀번호를 입력해주세요.")
    private String password;

    /** GUARDIAN: 아이 소속 유치원(히든), TEACHER/KINDERGARTEN_ADMIN: 선택 유치원 */
    private Long kindergartenId;

    @NotBlank(message = "이름을 입력해주세요.")
    private String name;

    /** 양육자·유치원 관계자 가입 시 필수 (서비스에서 검증) */
    @Length(min = 6, max = 6, message = "주민등록번호 앞자리는 숫자 6자리여야 합니다.")
    private String rrnFirst6;

    @Length(min = 7, max = 7, message = "주민등록번호 뒷자리는 숫자 7자리여야 합니다.")
    private String rrnBack7;

    private GenderEnum gender;

    // ----- Guardian -----
    private String address;

    /** 아이 찾기 후 선택한 child PK */
    private Long childId;

    @Length(min = 6, max = 6, message = "주민등록번호 앞자리는 숫자 6자리여야 합니다.")
    private String childRrnFirst6;

    @Length(min = 7, max = 7, message = "주민등록번호 뒷자리는 숫자 7자리여야 합니다.")
    private String childRrnBack7;

    /** 프론트 공통코드: MOTHER, FATHER, MATERNAL_GRANDMOTHER 등 → 서버에서 DB 허용 값으로 매핑 */
    private String relationship;

    @JsonAlias("primaryGuardian")
    private Boolean isPrimaryGuardian;

    // ----- 유치원 관계자 (TEACHER / KINDERGARTEN_ADMIN) -----
    private String emergencyContactName;
    private String emergencyContactPhone;

    /** PRINCIPAL | VICE_PRINCIPAL | TEACHER 등 → LevelEnum 으로 매핑 */
    private String level;

    private String staffNo;

    private String startDate;
    private String endDate;

    // ----- Superadmin -----
    private String department;

    /** 프론트 히든용 (무시) */
    private String status;
}
