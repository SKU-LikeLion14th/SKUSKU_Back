# 과제 채점 302 / Mixed Content 이슈 정리

## 1. 현상

- 관리자 과제 채점 API 호출 시 브라우저에서 `302`, `Mixed Content`, `CORS`, `ERR_NETWORK` 형태로 보임
- 문제 요청 경로: `PUT https://backend.sku-sku.com/admin/assignment/check/feedback`
- 사용자 체감상 "채점 기능이 안 됨"으로 보이지만, 실제로는 채점 비즈니스 로직까지 들어가기 전에 인증/리다이렉트 단계에서 실패하고 있음

## 2. 확정된 원인

### 원인 A. 보호된 API가 로그인 리다이렉트(302)로 응답함

- `/admin/assignment/**` 는 `ADMIN_LION` 권한이 필요한 보호 경로임
- 인증이 비어 있거나 실패하면 API 요청도 브라우저용 로그인 흐름으로 빠지면서 `302` 를 반환함
- 따라서 이 문제는 `AssignmentService.checkOrFeedbackSubmittedAssignment()` 내부 로직 에러가 아니라 보안 계층에서 먼저 발생한 문제임

### 원인 B. 리다이렉트 URL 이 HTTPS가 아니라 HTTP로 생성됨

- 실서버 확인 결과:
  - `curl -I https://backend.sku-sku.com/admin/assignment/check/feedback`
  - 응답: `302 Location: http://backend.sku-sku.com/oauth2/authorization/google`
- 실서버 확인 결과:
  - `curl -I https://backend.sku-sku.com/log/status`
  - 응답: `302 Location: http://backend.sku-sku.com/oauth2/authorization/google`
- 실서버 확인 결과:
  - `curl -I https://backend.sku-sku.com/oauth2/authorization/google`
  - Google 리다이렉트는 되지만 `redirect_uri=http://backend.sku-sku.com/login/oauth2/code/google`
- 즉 HTTPS 환경에서 인증 리다이렉트가 HTTP 주소로 생성되고 있고, 이 때문에 브라우저가 `Mixed Content` 로 차단함

### 원인 C. 브라우저 최종 에러와 서버 첫 원인이 다름

- 서버의 첫 응답은 실제로 `302` 가 맞음
- 다만 브라우저는 그 다음 단계에서 HTTP 리다이렉트를 막으므로 최종적으로 `CORS`, `ERR_NETWORK`, `Mixed Content` 처럼 보임
- 따라서 "302인가 아닌가"의 답은 둘 다 맞지만 순서가 다름
  - 서버 첫 이벤트: `302`
  - 브라우저 최종 표면 에러: `Mixed Content/CORS/ERR_NETWORK`

## 3. 프론트엔드 코드 확인 결과

- 현재 확인한 프론트 코드는 1차 원인으로 보이지 않음
- `src/utils/axios.js`
  - `baseURL: "https://backend.sku-sku.com"`
  - `withCredentials: true`
- `src/pages/Admin/Assignment/CheckDetails.jsx`
  - `API.put("/admin/assignment/check/feedback", requestData)` 형태로 정상 호출
- `src/components/GoogleLoginBtn.jsx`
  - 로그인 시작 URL도 `https://backend.sku-sku.com/oauth2/authorization/google?...` 로 사용
- 결론:
  - 프론트가 애초에 `http://backend...` 로 잘못 호출해서 생긴 문제는 아님
  - 다만 추후에는 `401` 발생 시 재로그인 안내를 더 명확히 하는 UX 보완은 가능함

## 4. 지금 해결해야 할 항목

### P0. API 요청이 인증 실패 시 302가 아니라 401/403으로 응답하도록 변경

- 대상 영역:
  - `src/main/java/com/sku_sku/backend/security/SecurityConfig.java`
- 해결 목적:
  - XHR / fetch / axios 요청이 브라우저용 OAuth2 로그인 리다이렉트로 빠지지 않게 하기
- 기대 결과:
  - 프론트는 `302 + Mixed Content` 대신 명확한 `401` 또는 `403` 을 받게 됨

### P0. HTTPS 프록시 뒤에서 absolute redirect URL 이 HTTP로 생성되는 문제 수정

- 대상 영역:
  - Spring forward header 처리
  - 배포 환경 Nginx 또는 프록시의 `X-Forwarded-Proto` 전달 여부
- 리포지토리 기준 확인 사항:
  - 현재 코드상 `ForwardedHeaderFilter`
  - `server.forward-headers-strategy`
  - `X-Forwarded-Proto` 대응 설정이 확인되지 않음
- 기대 결과:
  - OAuth 시작 URL과 Google `redirect_uri` 가 모두 `https://backend.sku-sku.com/...` 으로 생성되어야 함

### P1. 왜 해당 시점에 인증이 비었다고 판단됐는지 추가 확인

- 아직 미확정인 부분:
  - `access_token` 쿠키 만료 또는 미전송
  - `ADMIN_LION` refresh 흐름 실패
  - 세션/쿠키 상태 불일치
- 확인 대상:
  - `src/main/java/com/sku_sku/backend/security/JwtAuthenticationFilter.java`
  - `src/main/java/com/sku_sku/backend/service/OAuth2Service.java`
  - 실제 로그인 직후 `Set-Cookie`
  - 브라우저 HAR 또는 서버 로그

### P2. 프론트 예외 처리 보강

- 현재 프론트는 이 케이스를 `피드백 처리 실패` 또는 `Network Error` 로만 보여줌
- 이후 보완 가능 항목:
  - `401` 이면 "로그인이 만료되었습니다" 안내
  - 재로그인 유도
  - `403` 이면 권한 부족 메시지 분리

## 5. 코드상 직접 연관된 주요 파일

### 백엔드

- `src/main/java/com/sku_sku/backend/security/SecurityConfig.java`
- `src/main/java/com/sku_sku/backend/security/OAuth2SuccessHandler.java`
- `src/main/java/com/sku_sku/backend/security/JwtAuthenticationFilter.java`
- `src/main/java/com/sku_sku/backend/service/OAuth2Service.java`
- `src/main/java/com/sku_sku/backend/controller/admin/AssignmentAdminController.java`

### 프론트엔드

- `src/utils/axios.js`
- `src/pages/Admin/Assignment/CheckDetails.jsx`
- `src/components/GoogleLoginBtn.jsx`
- `src/components/AdminRouteGuard.jsx`

## 6. 수정 후 검증 기준

### 필수 검증

- `PUT /admin/assignment/check/feedback` 가 미인증 상태일 때 `302` 가 아니라 `401` 또는 `403` 으로 떨어져야 함
- `curl -I https://backend.sku-sku.com/oauth2/authorization/google` 결과에서 Google `redirect_uri` 가 `https://backend.sku-sku.com/login/oauth2/code/google` 이어야 함
- 브라우저 콘솔에 `Mixed Content` 가 더 이상 발생하지 않아야 함
- 프론트에서 채점 실패 시 원인이 `Network Error` 로 뭉개지지 않아야 함

### 정상 플로우 검증

- 로그인된 `ADMIN_LION` 이 채점 API 호출 시 `200 OK`
- 채점 완료 후 이메일 발송 흐름 정상 동작
- 만료된 토큰 상태에서도 refresh 정책이 의도대로 동작하는지 확인

## 7. 현재 결론 한 줄 요약

현재 채점 기능 장애의 핵심은 "관리자 채점 API가 인증 실패로 302 로그인 리다이렉트를 내보내는데, 그 리다이렉트 URL 이 HTTP 로 생성되어 브라우저가 Mixed Content/CORS/ERR_NETWORK 로 막는다" 이다.
