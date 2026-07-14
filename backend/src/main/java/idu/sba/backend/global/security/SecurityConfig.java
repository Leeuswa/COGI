package idu.sba.backend.global.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
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
                        // /api/auth/ 로 시작하는 모든 경로 허용(로그인/회원가입/코드발송)
                        .requestMatchers("/api/auth/**","/error").permitAll()

                        // /api/guest/** 로 시작하는 모든 경로 허용(비회원 로그인)
                        .requestMatchers("/api/guest/**").permitAll()


                        // 나머지 모든 요청은 토큰 필요, 없으면 401에러
                        .anyRequest().authenticated()
                )
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
