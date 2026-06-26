package com.ai_kids_care.v1.dataplatform;

import com.ai_kids_care.v1.dto.ClassCreateDTO;
import com.ai_kids_care.v1.entity.Class;
import com.ai_kids_care.v1.mapper.ClassMapperImpl;
import com.ai_kids_care.v1.type.StatusEnum;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Behavioural complement to the INC-005 compile-time guard
 * ({@code @Mapper(unmappedTargetPolicy = ERROR)} on all mappers, which makes a forgotten
 * target a compile error). The compile guard proves completeness; this round-trip test proves
 * the mapping is also correct (right fields, enum coercion) for a representative write-path
 * mapper, and that intentionally-ignored targets stay unset.
 */
class MapperCompletenessTest {

    private final ClassMapperImpl mapper = new ClassMapperImpl();

    @Test
    void classCreateDto_mapsAllDataFields_andLeavesIgnoredTargetsUnset() {
        ClassCreateDTO dto = new ClassCreateDTO();
        dto.setName("Sunflower");
        dto.setGrade("3");
        dto.setAcademicYear(2026L);
        dto.setStartDate(LocalDate.of(2026, 3, 1));
        dto.setEndDate(LocalDate.of(2027, 2, 28));
        dto.setStatus("ACTIVE");

        Class entity = mapper.toEntity(dto);

        // data fields mapped (incl. String -> StatusEnum coercion)
        assertThat(entity.getName()).isEqualTo("Sunflower");
        assertThat(entity.getGrade()).isEqualTo("3");
        assertThat(entity.getAcademicYear()).isEqualTo(2026L);
        assertThat(entity.getStartDate()).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(entity.getEndDate()).isEqualTo(LocalDate.of(2027, 2, 28));
        assertThat(entity.getStatus()).isEqualTo(StatusEnum.ACTIVE);

        // intentionally-ignored, server-derived targets stay unset
        assertThat(entity.getId()).isNull();
        assertThat(entity.getKindergarten()).isNull();
        assertThat(entity.getCreatedAt()).isNull();
        assertThat(entity.getUpdatedAt()).isNull();
    }
}
