package com.ai_kids_care.v1.dto;

import com.ai_kids_care.v1.entity.Kindergarten;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 회원가입 등 공개 유치원 조회 응답 — 프론트 KindergartenLookupItem 과 필드 정렬 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KindergartenLookupDto {

    @JsonProperty("kindergartenId")
    private Long kindergartenId;
    private String name;
    private String code;
    private String address;
    @JsonProperty("regionCode")
    private String regionCode;
    @JsonProperty("businessRegistrationNo")
    private String businessRegistrationNo;
    @JsonProperty("contactName")
    private String contactName;
    @JsonProperty("contactPhone")
    private String contactPhone;
    @JsonProperty("contactEmail")
    private String contactEmail;
    private String status;

    public static KindergartenLookupDto fromEntity(Kindergarten k) {
        return KindergartenLookupDto.builder()
                .kindergartenId(k.getId())
                .name(k.getName())
                .code(k.getCode())
                .address(k.getAddress())
                .regionCode(k.getRegionCode())
                .businessRegistrationNo(k.getBusinessRegistrationNo())
                .contactName(k.getContactName())
                .contactPhone(k.getContactPhone())
                .contactEmail(k.getContactEmail())
                .status(k.getStatus() != null ? k.getStatus().name() : null)
                .build();
    }
}
