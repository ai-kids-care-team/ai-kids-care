package com.ai_kids_care.v1.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class EffectiveAuthorizationContextFilter extends OncePerRequestFilter {

    private final EffectiveAuthorizationContextService contextService;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof SessionPrincipal principal)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            EffectiveAuthorizationContext context =
                    contextService.resolve(principal, request.getSession(false));
            EffectiveAuthorizationContextHolder.set(context);
            filterChain.doFilter(request, response);
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode().value() != HttpStatus.UNAUTHORIZED.value()) {
                throw exception;
            }
            if (request.getSession(false) != null) {
                request.getSession(false).invalidate();
            }
            SecurityContextHolder.clearContext();
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getWriter(), Map.of(
                    "error",
                    "Authentication failed"
            ));
        } finally {
            EffectiveAuthorizationContextHolder.clear();
        }
    }
}
