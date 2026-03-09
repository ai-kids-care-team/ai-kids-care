package com.dashboard.dto;

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
}
