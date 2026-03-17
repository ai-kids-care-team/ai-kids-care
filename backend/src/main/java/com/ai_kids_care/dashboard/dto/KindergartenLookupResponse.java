package com.ai_kids_care.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class KindergartenLookupResponse {
    private Long kindergartenId;
    private String name;
    private String code;
    private String address;
    private String regionCode;
    private String businessRegistrationNo;
    private String contactName;
    private String contactPhone;
    private String contactEmail;
    private String status;
}
