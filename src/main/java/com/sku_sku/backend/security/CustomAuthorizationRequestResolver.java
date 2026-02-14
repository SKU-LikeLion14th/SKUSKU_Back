package com.sku_sku.backend.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;

import java.time.Duration;

// (준하) OAuth2 인증 요청 시 프론트가 보낸 redirect URL을 Redis에 저장하고,
// state 파라미터는 Spring Security 기본값(CSRF 토큰)을 유지하도록 수정
@Component
public class CustomAuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {

    private final OAuth2AuthorizationRequestResolver defaultResolver;
    private final RedisTemplate<String, String> redisTemplate; // (준하) redirect URL 저장용

    public CustomAuthorizationRequestResolver(ClientRegistrationRepository repo,
            RedisTemplate<String, String> redisTemplate) {
        this.defaultResolver = new DefaultOAuth2AuthorizationRequestResolver(repo, "/oauth2/authorization");
        this.redisTemplate = redisTemplate; // (준하) RedisTemplate 주입 추가
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        OAuth2AuthorizationRequest req = defaultResolver.resolve(request);
        return customize(req, request);
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
        OAuth2AuthorizationRequest req = defaultResolver.resolve(request, clientRegistrationId);
        return customize(req, request);
    }

    // (준하) 프론트가 보낸 state(=redirect URL)를 Redis에 저장, Spring Security state는 유지
    private OAuth2AuthorizationRequest customize(OAuth2AuthorizationRequest request,
            HttpServletRequest servletRequest) {
        if (request == null)
            return null;

        // (준하) 프론트가 ?state=/dashboard 형태로 보내는 redirect URL
        String redirectUrl = servletRequest.getParameter("state");

        if (redirectUrl == null || redirectUrl.isBlank()) {
            redirectUrl = "/";
        }

        // (준하) Spring Security가 생성한 CSRF 보호용 state 값
        String securityState = request.getState();

        // (준하) redirect URL을 Redis에 별도 저장 (TTL 5분)
        redisTemplate.opsForValue().set("oauth2_redirect:" + securityState, redirectUrl, Duration.ofMinutes(5));

        // (준하) state 변경 없이 원본 그대로 반환 (CSRF 보호 유지)
        return request;
    }
}
