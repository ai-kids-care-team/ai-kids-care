package com.ai_kids_care.v1.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * DTO for {@link com.ai_kids_care.v1.entity.Announcement}
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class AnnouncementCreateDTO implements Serializable {
    @NotBlank
    @Size(max = 200)
    private String title;
    @NotBlank
    private String body;
    @NotNull
    private Boolean isPinned;
    private OffsetDateTime pinnedUntil;
    @NotNull
    private String status;
    private OffsetDateTime publishedAt;
    private OffsetDateTime startsAt;
    private OffsetDateTime endsAt;
}
