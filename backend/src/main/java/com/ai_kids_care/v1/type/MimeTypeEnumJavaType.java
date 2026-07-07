package com.ai_kids_care.v1.type;

import org.hibernate.type.descriptor.java.EnumJavaType;

/**
 * D-STORE fix: the PostgreSQL native enum {@code mime_type_enum} stores MIME-string labels
 * ({@code image/jpeg}, {@code image/png}, {@code video/mp4}) that are not valid Java identifiers,
 * so {@link MimeTypeEnum}'s Java constant names ({@code IMAGE_JPEG}, ...) necessarily diverge from
 * the DB labels. Hibernate's default {@link EnumJavaType} round-trips a named enum via
 * {@code Enum.name()}/{@code Enum.valueOf(name)}, which would throw
 * {@code IllegalArgumentException: No enum constant ... image/jpeg} the first time a row is
 * actually read through JPA. Overriding {@link #toName}/{@link #fromName} to go through
 * {@link MimeTypeEnum#getValue()}/{@link MimeTypeEnum#fromValue(String)} instead keeps the DB
 * schema/labels untouched (no migration) while making the Java-side mapping correct.
 */
public class MimeTypeEnumJavaType extends EnumJavaType<MimeTypeEnum> {

    public MimeTypeEnumJavaType() {
        super(MimeTypeEnum.class);
    }

    @Override
    public String toName(MimeTypeEnum value) {
        return value == null ? null : value.getValue();
    }

    @Override
    public MimeTypeEnum fromName(String name) {
        return name == null ? null : MimeTypeEnum.fromValue(name);
    }
}
