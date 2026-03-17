package com.ai_kids_care.dashboard.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class SignupRequest {

    private String name;
    private String loginId;
    private String password;
    private String email;
    private String phone;
    private String memberType;
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
