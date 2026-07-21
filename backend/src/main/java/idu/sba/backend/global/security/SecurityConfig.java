package idu.sba.backend.global.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtProvider jwtProvider;
    private final CustomOAuth2UserService customOAuth2UserService;
    private final OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, CorsConfigurationSource corsConfigurationSource) throws Exception {
        http
                //CSRF 방어는 세션/쿠 기반일 때 필요함. JWT를 헤더로 주고받기 때문에 해당없음(비활성화)
                .csrf(csrf -> csrf.disable())
                //프론트(React)를 사용하기 위해 다른 포트에서 호출하는 것을 허용함
                .cors(cors -> cors.configurationSource(corsConfigurationSource))

                .sessionManagement(sm ->
                        sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // /api/auth/ 로 시작하는 모든 경로 허용(로그인/회원가입/코드발송/소셜로그인)
                        .requestMatchers("/api/auth/**","/oauth2/**","/login/**","/error","/api/terms",
                                "/api/users/me/github/callback").permitAll()
                        // /api/guest/** 로 시작하는 모든 경로 허용(비회원 로그인)
                        .requestMatchers("/api/guest/**").permitAll()
                        // /api/plans 요금제 목록 조회 (GET만 허용)
                        .requestMatchers(HttpMethod.GET, "/api/plans").permitAll()
                        // GitHub Webhook 수신(API-024) — JWT가 아니라 X-Hub-Signature-256 서명으로 자체 인증
                        .requestMatchers(HttpMethod.POST, "/api/webhooks/github").permitAll()
                        // 관리자 전용 — JwtAuthenticationFilter가 ROLE_ADMIN 권한을 심으므로 hasRole로 막는다(비관리자 403)
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        // 나머지 모든 요청은 토큰 필요, 없으면 401에러
                        .anyRequest().authenticated()
                )
                // 인증 안 된 요청 → 401 JSON
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpStatus.UNAUTHORIZED.value());   // 401
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.setCharacterEncoding("UTF-8");
                            response.getWriter().write(
                                    "{\"success\":false,\"message\":\"로그인이 필요합니다.\",\"data\":null}");
                        })
                )
                // OAuth2 로그인 연결
                .oauth2Login(oauth -> oauth
                        .userInfoEndpoint(userInfo -> userInfo.userService(customOAuth2UserService))
                        .successHandler(oAuth2LoginSuccessHandler)
                        .failureHandler((request, response, exception) -> {   // ← 추가
                            String code = (exception instanceof OAuth2AuthenticationException oae)
                                    ? oae.getError().getErrorCode() : "social";
                            response.sendRedirect("http://localhost:5173/login?error=" + code);
                        }))
                //요청이 오기전에 토큰 검사 후 인증 등록
                        .addFilterBefore(new JwtAuthenticationFilter(jwtProvider), UsernamePasswordAuthenticationFilter.class);

                return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        //React 개발주소 여기 없는 출처는 브라우저가 차단.
        config.setAllowedOrigins(List.of("http://localhost:5173", "http://localhost:3000"));
        // 허용할 HTTP 메서드
        config.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"));
        // 허용할 요청 헤더. "*" = 전부 허용 (Authorization 토큰 헤더 포함).
        config.setAllowedHeaders(List.of("*"));
        // 인증정보(쿠키/Authorization 등) 포함 요청 허용 여부.
        config.setAllowCredentials(true);

        //모든경로 허용
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
