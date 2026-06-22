package com.ai_kids_care.v1.config;

import jakarta.servlet.DispatcherType;
import com.ai_kids_care.v1.security.AiServiceTokenAuthenticationFilter;
import com.ai_kids_care.v1.security.EffectiveAuthorizationContextFilter;
import com.ai_kids_care.v1.security.audit.CorrelationIdFilter;
import com.ai_kids_care.v1.security.audit.SecurityAuditAccessDeniedHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.DelegatingSecurityContextRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            SecurityContextRepository securityContextRepository,
            CookieCsrfTokenRepository csrfTokenRepository,
            EffectiveAuthorizationContextFilter authorizationContextFilter,
            AiServiceTokenAuthenticationFilter aiServiceTokenAuthenticationFilter,
            SecurityAuditAccessDeniedHandler accessDeniedHandler,
            CorrelationIdFilter correlationIdFilter
    ) throws Exception {
        CsrfTokenRequestAttributeHandler csrfTokenRequestHandler =
                new CsrfTokenRequestAttributeHandler();
        csrfTokenRequestHandler.setCsrfRequestAttributeName(null);

        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        .csrfTokenRequestHandler(csrfTokenRequestHandler))
                .securityContext(context -> context
                        .securityContextRepository(securityContextRepository)
                        .requireExplicitSave(true))
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(
                                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**",
                                "/v3/api-docs.yaml/**"
                        ).permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // Public surface required before a session exists: CSRF bootstrap,
                        // registration availability, and S3 reference/directory reads used by
                        // the signup forms (kindergarten directory, common codes, menus).
                        // These carry no S0/S1; every mutating operation stays authenticated
                        // and policy-gated. Closed/removed paths (e.g. /auth/refresh,
                        // /children/rrn, /detection_events) are intentionally absent so they
                        // fall through to default-deny instead of an anonymous existence oracle.
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/auth/csrf",
                                "/api/v1/auth/register/availability",
                                "/api/v1/kindergartens/**",
                                "/api/v1/common_codes/**",
                                "/api/v1/menus/**"
                        ).permitAll()
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/auth/login",
                                "/api/v1/auth/register",
                                "/api/v1/auth/guardian-child-verifications"
                        ).permitAll()
                        // ADR-0026 Phase 2：内部凭据接口仅限 AI 服务（Bearer token → ROLE_AI_SERVICE）。
                        // 必须在通配 /api/v1/** 规则之前；普通会话用户（SESSION_AUTHENTICATED）→ 403。
                        .requestMatchers("/api/v1/internal/**").hasRole("AI_SERVICE")
                        .requestMatchers("/api/v1/**").authenticated()
                        .anyRequest().authenticated()
                )
                // correlation id 须最先设置（MDC + 响应头），使认证失败 / 授权拒绝路径也带 id。
                // 同时以 @Component(HIGHEST_PRECEDENCE) 注册为顶层 servlet filter；OncePerRequestFilter
                // 去重，保证在 MockMvc 与生产中都恰好执行一次且位于安全链之前。
                .addFilterBefore(
                        correlationIdFilter,
                        SecurityContextHolderFilter.class
                )
                .addFilterAfter(
                        authorizationContextFilter,
                        SecurityContextHolderFilter.class
                )
                // AI 服务 Bearer token filter：在授权检查前设入 ROLE_AI_SERVICE 认证（仅作用于 /api/v1/internal/**）。
                .addFilterAfter(
                        aiServiceTokenAuthenticationFilter,
                        SecurityContextHolderFilter.class
                );
        return http.build();
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new DelegatingSecurityContextRepository(
                new RequestAttributeSecurityContextRepository(),
                new HttpSessionSecurityContextRepository()
        );
    }

    @Bean
    public SessionAuthenticationStrategy sessionAuthenticationStrategy() {
        return new ChangeSessionIdAuthenticationStrategy();
    }

    @Bean
    public CookieCsrfTokenRepository csrfTokenRepository() {
        return CookieCsrfTokenRepository.withHttpOnlyFalse();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList(
                                        "http://localhost",
                                        "http://localhost:80",
                                        "http://localhost:3000",
                                        "http://127.0.0.1",
                                        "http://127.0.0.1:3000",
                                        "http://frontend"
                                        ));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS" , "PATCH"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
