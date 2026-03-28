package com.ai_kids_care.v1.type;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

@Getter
public enum MimeTypeEnum {

    IMAGE_JPEG("image/jpeg"),
    IMAGE_PNG("image/png"),
    VIDEO_MP4("video/mp4");

    @JsonValue
    private final String value;

    MimeTypeEnum(String value) {
        this.value = value;
    }

    @JsonCreator
    public static MimeTypeEnum fromValue(String value) {
        for (MimeTypeEnum type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown mime type: " + value);
    }
}