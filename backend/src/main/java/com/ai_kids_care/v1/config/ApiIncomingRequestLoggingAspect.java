package com.ai_kids_care.v1.config;

import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * {@link com.ai_kids_care.v1.api.AuthApi} 에 정의된 엔드포인트의 실제 구현체
 * {@link com.ai_kids_care.v1.controller.AuthApiController} 로 들어오는 HTTP 요청을 로깅합니다.
 * <p>
 * {@code AuthApi} 인터페이스는 OpenAPI Generator 로 생성되므로, 생성 파일을 직접 수정하지 않고
 * 이 Aspect 로 수신 로그를 남깁니다.
 */
@Aspect
@Component
public class ApiIncomingRequestLoggingAspect {

    private static final Logger log = LoggerFactory.getLogger(ApiIncomingRequestLoggingAspect.class);

    /**
     * {@code execution(* 패키지.클래스.메서드(..))} 형태여야 합니다.
     * {@code controller.*(..)} 만 두면 타입명으로 해석되어 컴파일 경고/실패(invalidAbsoluteTypeName)가 납니다.
     */
    @Before("execution(* com.ai_kids_care.v1.controller..*.*(..))")
    public void logIncomingApiCall(JoinPoint joinPoint) {
        String handlerMethod = joinPoint.getSignature().getName();
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            HttpServletRequest req = attrs.getRequest();
            String remote = req.getRemoteAddr();
            log.info("[API] {} {} | handler={} | remote={}",
                    req.getMethod(),
                    req.getRequestURI(),
                    handlerMethod,
                    remote);
        } else {
            log.info("[API] handler={} (no request context)", handlerMethod);
        }
    }
}
