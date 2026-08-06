package idu.sba.backend.global.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;


@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtProvider jwtProvider;


    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException{
            //요청 헤더에서 토큰 가져오기
            String token = resolveToken(request);

        //토큰이 있고 유효하면 → 인증 정보를 SecurityContext에 저장
        if(token != null && jwtProvider.validateToken(token)){
            Long userId = jwtProvider.getUserId(token);
            String role = jwtProvider.getRole(token);

            var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));
            var authentication = new UsernamePasswordAuthenticationToken(userId,null,authorities);
            SecurityContextHolder.getContext().setAuthentication(authentication);

         }
        //다음 필터로 넘김
        filterChain.doFilter(request,response);

    }

    // Authorization 헤더에서 "Bearer xxx" 형태의 토큰만 추출
    private String resolveToken(HttpServletRequest request) {


        //쿠키 찾기
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (CookieUtil.ACCESS_TOKEN.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        //쿠키에 없으면 헤더로 찾기
        String bearer = request.getHeader("Authorization");
        if (bearer != null && bearer.startsWith("Bearer ")) {
            return bearer.substring(7); // "Bearer " 7글자 잘라내고 토큰
        }
        return null; //토큰 없으면 null
    }


}
