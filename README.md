# 📋 SKUSKU_Back 수정 일지

> 프로젝트 수정 내역을 날짜별로 기록합니다.
> 공동 작업자들이 변경사항을 쉽게 파악할 수 있도록 작성합니다.

---

## 2026-02-14 (준하)

### [fix] OAuth2 재로그인 에러 수정 — Redis 기반 state 관리

**문제**: 기존 사용자가 Google 로그인을 다시 시도하면 OAuth2 인증 에러가 발생함

**원인**:
1. `state` 파라미터(CSRF 보안 토큰)를 redirect URL로 덮어써서 보안 검증이 깨짐
2. `STATELESS` 세션 정책에서 OAuth2 인증 요청을 세션에 저장할 수 없어 재로그인 시 검증 실패

**해결 방법**: redirect URL은 Redis에 별도 저장하고, `state`는 Spring Security 기본 CSRF 토큰을 유지하도록 수정

**수정 파일**:

| 파일 경로 | 작업 | 설명 |
|----------|------|------|
| `src/main/java/.../security/RedisOAuth2AuthorizationRequestRepository.java` | 🆕 신규 | 세션 대신 Redis에 OAuth2 인증 요청을 저장/조회/삭제하는 저장소 |
| `src/main/java/.../security/CustomAuthorizationRequestResolver.java` | ✏️ 수정 | state 남용 제거, 프론트가 보낸 redirect URL을 Redis에 별도 저장 |
| `src/main/java/.../security/OAuth2SuccessHandler.java` | ✏️ 수정 | state 직접 사용 → Redis에서 redirect URL 조회 후 삭제(일회용) |
| `src/main/java/.../security/SecurityConfig.java` | ✏️ 수정 | Redis 기반 `AuthorizationRequestRepository` 등록 |

> ⚠️ 프론트엔드 변경 없음. 기존대로 `?state=/path` 형태로 호출하면 됩니다.

---
