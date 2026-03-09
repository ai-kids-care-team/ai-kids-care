package com.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@AllArgsConstructor
public class ChildLookupResponse {

    private Long childId;
    private Long kindergartenId;
    private Long classId;
    private String className;
    private String name;
    private String childNo;
    private LocalDate birthDate;
    private String gender;
}
