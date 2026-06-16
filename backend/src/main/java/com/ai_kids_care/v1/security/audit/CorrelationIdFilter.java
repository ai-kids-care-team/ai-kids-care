package com.ai_kids_care.v1.security.audit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 为每个请求分配 request correlation id（SPEC-0001 §282），供日志（MDC）与安全审计写入共享。
 *
 * 以 {@link Ordered#HIGHEST_PRECEDENCE} 注册，包裹整条 Spring Security 过滤链，
 * 使认证失败 / 授权拒绝路径也带 correlation id。
 *
 * correlation id 是新生成的随机值（或经净化的入站 {@code X-Correlation-Id} 头），
 * **绝不**使用 session id（session id 属 S0，不进审计 detail——security-architecture §40）。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String MDC_KEY = "correlationId";
    public static final String HEADER = "X-Correlation-Id";

    /** 入站头白名单：仅接受短的字母数字 / 连字符 / 下划线，避免日志注入与超长值。 */
    private static final Pattern SAFE = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String correlationId = resolve(request.getHeader(HEADER));
        MDC.put(MDC_KEY, correlationId);
        response.setHeader(HEADER, correlationId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    private String resolve(String inbound) {
        if (inbound != null && SAFE.matcher(inbound).matches()) {
            return inbound;
        }
        return UUID.randomUUID().toString();
    }
}
