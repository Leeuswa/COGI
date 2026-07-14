package idu.sba.backend.domain.guest.controller;


import idu.sba.backend.domain.guest.dto.GuestReviewRequest;
import idu.sba.backend.domain.guest.dto.GuestReviewResponse;
import idu.sba.backend.domain.guest.service.GuestReviewService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class GuestReviewController {

    private final GuestReviewService guestReviewService;

    //"guest_token"이라는 이름으로 통일
    private static final String COOKIE_NAME = "guest_token";

    // 쿠키 유지시간(초 단위). 24시간
    private static final int COOKIE_MAX_AGE = 60 * 60 * 24;

    @PostMapping("/api/guest/local-review")
    public ResponseEntity<?> createGuestReview(
            @RequestBody GuestReviewRequest request, //프론트가 보낸 JSON(code ,language)이 여기에 담김
            HttpServletRequest httpRequest, //브라우저가 보낸 요청 전체(쿠키 읽을 때)
            HttpServletResponse httpResponse // 브라우저에게 보낼 응답 (쿠키 심을 때)
    ) {
        // 쿠키에서 guest_token 읽기 (없으면 새로 생성)
        String guestToken = readGuestTokenFromCookie(httpRequest);

        // 쿠키가 없다면
        if (guestToken == null) {
            guestToken = UUID.randomUUID().toString();

            Cookie cookie = new Cookie(COOKIE_NAME, guestToken);
            cookie.setPath("/"); //사이트 전체에서 쿠키가 통함
            cookie.setHttpOnly(true); // 자바스크립트 훔쳐가기 방지
            cookie.setMaxAge(COOKIE_MAX_AGE); // 24시간뒤 브라우저에서 자동 삭제
            httpResponse.addCookie(cookie); // 이 쿠키를 응답에 실어 브라우저 전달
        }

        // Service 호출
        try {
            GuestReviewResponse response = guestReviewService.createReview(request, guestToken);
            return ResponseEntity.ok(response); // 성공시 200 호출

        } catch (GuestReviewService.TrialLimitExceededException e) {
            //예외 처리 3회 이상시 403 응답으로 변환 (성공/실패 모두 JSON으로 통일)
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", e.getMessage()));
        }
    }

    //"guest_token"이름의 쿠키 값을 찾음 없으면 null 반환
    private String readGuestTokenFromCookie(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : request.getCookies()) {
            if (COOKIE_NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}