package com.ai_kids_care.v1.type;

/**
 * DB {@code mime_type_enum} 라벨({@code image/jpeg} 등)과 매핑.
 * Java 식별자 제약으로 상수명과 PG 라벨이 달라 {@link com.ai_kids_care.v1.converter.MimeTypeEnumConverter} 사용.
 */
public enum MimeTypeEnum {
    IMAGE_JPEG("image/jpeg"),
    IMAGE_PNG("image/png"),
    VIDEO_MP4("video/mp4");

    private final String pgLabel;

    MimeTypeEnum(String pgLabel) {
        this.pgLabel = pgLabel;
    }

    public String getPgLabel() {
        return pgLabel;
    }
}
