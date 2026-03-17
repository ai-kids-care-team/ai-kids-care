package com.ai_kids_care.v1.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.NotNull;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
@EqualsAndHashCode
@Embeddable
public class ChildGuardianRelationshipId implements Serializable {
    private static final long serialVersionUID = -1759809882610782201L;
    @NotNull
    @Column(name = "kindergarten_id", nullable = false)
    private Long kindergartenId;

    @NotNull
    @Column(name = "child_id", nullable = false)
    private Long childId;

    @NotNull
    @Column(name = "guardian_id", nullable = false)
    private Long guardianId;


}