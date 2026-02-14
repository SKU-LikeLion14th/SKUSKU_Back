package com.sku_sku.backend.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;

// (준하) 세션 대신 Redis에 OAuth2 인증 요청을 저장하는 커스텀 저장소
// STATELESS 세션 정책에서도 OAuth2 로그인이 정상 동작하도록 함
@Component
@RequiredArgsConstructor
public class RedisOAuth2AuthorizationRequestRepository
        implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String REDIS_KEY_PREFIX = "oauth2_auth_request:"; // Redis 키 접두사
    private static final Duration TTL = Duration.ofMinutes(5); // 인증 요청 유효 시간

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        // (준하) 콜백 시 state 파라미터로 Redis에서 인증 요청 조회
        String state = request.getParameter("state");
        if (state == null)
            return null;

        String json = redisTemplate.opsForValue().get(REDIS_KEY_PREFIX + state);
        if (json == null)
            return null;

        return deserialize(json);
    }

    @Override
    public void saveAuthorizationRequest(OAuth2AuthorizationRequest authorizationRequest, HttpServletRequest request,
            HttpServletResponse response) {
        // (준하) state 값을 키로 Redis에 인증 요청 저장 (TTL 5분)
        if (authorizationRequest == null) {
            removeAuthorizationRequest(request, response);
            return;
        }

        String state = authorizationRequest.getState();
        String json = serialize(authorizationRequest);
        redisTemplate.opsForValue().set(REDIS_KEY_PREFIX + state, json, TTL);
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(HttpServletRequest request,
            HttpServletResponse response) {
        // (준하) 인증 완료 시 Redis에서 인증 요청 삭제 (일회용)
        String state = request.getParameter("state");
        if (state == null)
            return null;

        String json = redisTemplate.opsForValue().get(REDIS_KEY_PREFIX + state);
        if (json == null)
            return null;

        redisTemplate.delete(REDIS_KEY_PREFIX + state);
        return deserialize(json);
    }

    // (준하) OAuth2AuthorizationRequest를 JSON 문자열로 직렬화
    private String serialize(OAuth2AuthorizationRequest request) {
        try {
            Map<String, Object> data = Map.of(
                    "authorizationUri", request.getAuthorizationUri(),
                    "clientId", request.getClientId(),
                    "redirectUri", request.getRedirectUri() != null ? request.getRedirectUri() : "",
                    "scopes", request.getScopes(),
                    "state", request.getState() != null ? request.getState() : "",
                    "additionalParameters", request.getAdditionalParameters(),
                    "authorizationRequestUri", request.getAuthorizationRequestUri(),
                    "attributes", request.getAttributes());
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("OAuth2AuthorizationRequest 직렬화 실패", e);
        }
    }

    // (준하) JSON 문자열을 OAuth2AuthorizationRequest로 역직렬화
    @SuppressWarnings("unchecked")
    private OAuth2AuthorizationRequest deserialize(String json) {
        try {
            Map<String, Object> data = objectMapper.readValue(json, Map.class);
            return OAuth2AuthorizationRequest.authorizationCode()
                    .authorizationUri((String) data.get("authorizationUri"))
                    .clientId((String) data.get("clientId"))
                    .redirectUri((String) data.get("redirectUri"))
                    .scopes(new java.util.HashSet<>((java.util.Collection<String>) data.get("scopes")))
                    .state((String) data.get("state"))
                    .additionalParameters((Map<String, Object>) data.get("additionalParameters"))
                    .authorizationRequestUri((String) data.get("authorizationRequestUri"))
                    .attributes((Map<String, Object>) data.get("attributes"))
                    .build();
        } catch (JsonProcessingException e) {
            throw new RuntimeException("OAuth2AuthorizationRequest 역직렬화 실패", e);
        }
    }
}
