package com.ai_kids_care.v1.converter;

import com.ai_kids_care.v1.type.MimeTypeEnum;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.postgresql.util.PGobject;

import java.sql.SQLException;

/**
 * PostgreSQL {@code mime_type_enum} (라벨에 {@code /} 포함) ↔ {@link MimeTypeEnum}.
 */
@Converter(autoApply = false)
public class MimeTypeEnumConverter implements AttributeConverter<MimeTypeEnum, Object> {

    @Override
    public Object convertToDatabaseColumn(MimeTypeEnum attribute) {
        if (attribute == null) {
            return null;
        }
        PGobject po = new PGobject();
        po.setType("mime_type_enum");
        try {
            po.setValue(attribute.getPgLabel());
        } catch (SQLException e) {
            throw new IllegalStateException("mime_type_enum binding failed", e);
        }
        return po;
    }

    @Override
    public MimeTypeEnum convertToEntityAttribute(Object dbData) {
        if (dbData == null) {
            return null;
        }
        String v = dbData.toString();
        for (MimeTypeEnum e : MimeTypeEnum.values()) {
            if (e.getPgLabel().equals(v)) {
                return e;
            }
        }
        throw new IllegalArgumentException("Unknown mime_type_enum value: " + v);
    }
}
