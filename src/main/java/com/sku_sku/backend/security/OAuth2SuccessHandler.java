package com.sku_sku.backend.security;

import com.sku_sku.backend.domain.Lion;
import com.sku_sku.backend.enums.RoleType;
import com.sku_sku.backend.exception.EmptyLionException;
import com.sku_sku.backend.repository.LionRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;

// (준하) OAuth2 로그인 성공 시 JWT 발급 및 redirect URL 처리 수정
// redirect URL을 state 파라미터 대신 Redis에서 조회하도록 변경
@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private final JwtUtility jwtUtility;
    private final LionRepository lionRepository;
    private final RedisTemplate<String, String> redisTemplate;

    @Value("${cookie.secure}")
    private boolean isSecure;

    @Value("${cookie.sameSite}")
    private String isSameSite;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication)
            throws IOException {

        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        String email = oAuth2User.getAttribute("email");

        Lion lion = lionRepository.findByEmail(email).orElseThrow(EmptyLionException::new);

        String jwt = jwtUtility.generateJwt(email, lion.getName(), lion.getTrackType(), lion.getRoleType());

        // JWT를 HttpOnly Cookie에 저장
        ResponseCookie cookie = ResponseCookie.from("access_token", jwt)
                .httpOnly(true)
                .secure(isSecure)
                .sameSite(isSameSite)
                .path("/")
                .maxAge(Duration.ofHours(1))
                .build();

        response.addHeader("Set-Cookie", cookie.toString());

        // ADMIN_LION일 경우만 Refresh Token 발급
        if (lion.getRoleType() == RoleType.ADMIN_LION) {
            String refreshToken = UUID.randomUUID().toString();
            redisTemplate.opsForValue().set("refresh:" + email, refreshToken, Duration.ofDays(30));
        }

        // (준하) Redis에서 redirect URL 조회 후 삭제 (일회용)
        String state = request.getParameter("state");
        String redirectUrl = null;
        if (state != null) {
            redirectUrl = redisTemplate.opsForValue().get("oauth2_redirect:" + state);
            if (redirectUrl != null) {
                redisTemplate.delete("oauth2_redirect:" + state); // (준하) 사용 후 삭제
            }
        }
        if (redirectUrl == null || redirectUrl.isBlank()) {
            redirectUrl = "/"; // (준하) 기본값
        }

        System.out.println("redirectUrl: " + redirectUrl);

        // 유저가 로그인 시도하기 전에 요청했던 URL로 리디렉트
        response.sendRedirect(redirectUrl);
    }
}