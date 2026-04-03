package com.ai_kids_care.v1.vo.graph;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class ChildGraphVO {

    private ChildNodeVO child;
    private ClassNodeVO classInfo;
    private TeacherNodeVO teacher;
    private KindergartenNodeVO kindergarten;
    private List<GuardianNodeVO> guardians;

    @Getter
    @Builder
    public static class ChildNodeVO {
        private Long childId;
        private String name;
        private String childNo;
        private String gender;
        private String status;
    }

    @Getter
    @Builder
    public static class ClassNodeVO {
        private Long classId;
        private String name;
        private String grade;
        private Integer academicYear;
        private String status;
    }

    @Getter
    @Builder
    public static class TeacherNodeVO {
        private Long teacherId;
        private String name;
        private String staffNo;
        private String level;
        private String status;
    }

    @Getter
    @Builder
    public static class KindergartenNodeVO {
        private Long kindergartenId;
        private String name;
        private String address;
        private String status;
    }

    @Getter
    @Builder
    public static class GuardianNodeVO {
        private Long guardianId;
        private String name;
        private String gender;
        private String status;
        private String relationship;
        private Boolean isPrimary;
        private Integer priority;
    }
}