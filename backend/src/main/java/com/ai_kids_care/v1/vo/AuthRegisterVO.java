package com.ai_kids_care.v1.vo;

/**
 * 회원가입 단계에서 로그인 ID·이메일·연락처 등의 중복 여부(입력 포커스 아웃 검사용).
 *
 * @param available 사용 가능하면 {@code true}
 * @param message     불가 시 사용자에게 보여줄 문구, 가능하면 {@code null}
 */
public record AuthRegisterVO(boolean available, String message) {
}