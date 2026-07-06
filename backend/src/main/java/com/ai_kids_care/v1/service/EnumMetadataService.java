package com.ai_kids_care.v1.service;

import com.ai_kids_care.v1.type.CameraStreamTypeEnum;
import com.ai_kids_care.v1.type.EventStatusEnum;
import com.ai_kids_care.v1.type.EventTypeEnum;
import com.ai_kids_care.v1.type.GenderEnum;
import com.ai_kids_care.v1.type.LevelEnum;
import com.ai_kids_care.v1.type.ProtocolEnum;
import com.ai_kids_care.v1.type.RelationshipEnum;
import com.ai_kids_care.v1.type.StatusEnum;
import com.ai_kids_care.v1.vo.EnumValueVO;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * ADR-0013: 플랫폼 참조 데이터를 (퇴역한) {@code common_codes} 딕셔너리 테이블 대신
 * 기존 {@code com.ai_kids_care.v1.type.*} enum 으로 공급한다.
 *
 * <p>논리 이름({@code gender}/{@code guardian_relationship}/...) → enum 클래스 매핑은
 * 정적 레지스트리로 분기하며, 각 enum 의 {@code code}(상수명) 목록을 선언 순서대로 반환한다.
 * 라벨은 프론트엔드 i18n 의 몫이라 코드만 노출한다. DB 의존 없음.
 */
@Service
public class EnumMetadataService {

    /** 논리 이름 → enum 클래스. 다수 테이블이 공유하는 status 는 단일 {@link StatusEnum} 으로 매핑. */
    private static final Map<String, Class<? extends Enum<?>>> REGISTRY = Map.of(
            "gender", GenderEnum.class,
            "guardian_relationship", RelationshipEnum.class,
            "teacher_level", LevelEnum.class,
            "status", StatusEnum.class,
            "event_type", EventTypeEnum.class,
            "event_status", EventStatusEnum.class,
            "camera_stream_type", CameraStreamTypeEnum.class,
            "protocol", ProtocolEnum.class
    );

    /**
     * 주어진 논리 이름의 enum 코드 목록을 선언 순서(sortOrder)와 함께 반환한다.
     *
     * @param name    논리 enum 이름 (대소문자 무시)
     * @param context 다중 테이블 공용 status 의 출처 테이블 구분용(현재는 동형이라 투과/무시). 향후 분화 시 사용.
     * @throws EntityNotFoundException 등록되지 않은 이름 → 404
     */
    public List<EnumValueVO> getValues(String name, String context) {
        String key = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        Class<? extends Enum<?>> enumClass = REGISTRY.get(key);
        if (enumClass == null) {
            throw new EntityNotFoundException("Unknown enum name: " + name);
        }
        Enum<?>[] constants = enumClass.getEnumConstants();
        return IntStream.range(0, constants.length)
                .mapToObj(i -> new EnumValueVO(constants[i].name(), i))
                .toList();
    }
}
