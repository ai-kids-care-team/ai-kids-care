package com.ai_kids_care.v1.security;

import com.ai_kids_care.v1.config.InternalAiServiceConfig;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * 内部 AI 服务 Bearer token 认证 filter — ADR-0026 Phase 2（OQ-1=A 静态共享 token）。
 *
 * <p>仅作用于 {@code /api/v1/internal/**}。校验 {@code Authorization: Bearer <token>} 头：
 * <ul>
 *   <li>与配置 token 常量时间比较一致 → 在 SecurityContext 设入授予 {@code ROLE_AI_SERVICE}
 *       的认证主体；下游 {@code .requestMatchers("/api/v1/internal/**").hasRole("AI_SERVICE")} 放行。</li>
 *   <li>缺失/不匹配 → 不设认证；交由安全链：匿名 → 入口点 401。</li>
 * </ul>
 *
 * <p>该认证不持久化（无 session）——AI 每次请求自带 token。普通浏览器 session 用户权限为
 * {@code SESSION_AUTHENTICATED}（非 {@code ROLE_AI_SERVICE}），访问内部端点 → 403。
 */
@Component
@RequiredArgsConstructor
public class AiServiceTokenAuthenticationFilter extends OncePerRequestFilter {

    static final String INTERNAL_PATH_PREFIX = "/api/v1/internal/";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String AI_SERVICE_PRINCIPAL = "ai-service";

    private final InternalAiServiceConfig config;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(INTERNAL_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)
                && constantTimeEquals(header.substring(BEARER_PREFIX.length()), config.getServiceToken())) {
            Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                    AI_SERVICE_PRINCIPAL,
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_AI_SERVICE"))
            );
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
        }
        // token 缺失/不匹配：不设认证，交由安全链 → 401（不在此处直接写响应，保持与全局入口点一致）。
        filterChain.doFilter(request, response);
    }

    /**
     * 常量时间字节比较，避免 token 校验产生计时侧信道。
     * {@link MessageDigest#isEqual} 在现代 JDK 中为常量时间实现。
     */
    private static boolean constantTimeEquals(String presented, String expected) {
        if (presented == null || expected == null) {
            return false;
        }
        return MessageDigest.isEqual(
                presented.getBytes(StandardCharsets.UTF_8),
                expected.getBytes(StandardCharsets.UTF_8));
    }
}
