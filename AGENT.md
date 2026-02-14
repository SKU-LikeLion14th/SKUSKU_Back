# 🤖 AGENT.md — AI 바이브코딩 가이드

> 이 문서는 AI 에이전트가 SKUSKU_Back 프로젝트를 빠르게 이해하고,
> 프로젝트 컨벤션에 맞는 코드를 생성할 수 있도록 작성된 가이드입니다.

---

## 1. 프로젝트 정체성

- **프로젝트명**: SKUSKU_Back
- **목적**: 멋쟁이사자처럼 성공회대학교(SKU) 14기 학회 관리 백엔드 API
- **운영 도메인**: `sku-sku.com` (프론트: React, `localhost:5173` 개발 / `sku-sku.com` 프로덕션)
- **조직 GitHub**: `SKU-LikeLion14th`
- **핵심 컨셉**: 사용자 = 🦁 **"사자(Lion)"**, 관리자 = **"운영진(ADMIN_LION)"**, 일반 회원 = **"아기사자(BABY_LION)"**

---

## 2. 기술 스택 & 버전

| 기술 | 버전 | 비고 |
|------|------|-----|
| Java | 21 | `languageVersion = JavaLanguageVersion.of(21)` |
| Spring Boot | 3.4.3 | |
| Gradle | Groovy DSL | `build.gradle` |
| Spring Data JPA | Boot 관리 | MySQL 연동 |
| Spring Security | Boot 관리 | OAuth2 + JWT |
| JJWT | 0.12.6 | JWT 토큰 발급/검증 |
| SpringDoc OpenAPI | 2.8.5 | Swagger UI |
| Redis | Boot 관리 | 토큰 관리 |
| AWS S3 SDK | 2.31.35 | Presigned URL + CloudFront |
| Google Cloud AI | 3.53.0 | Gemini 주관식 채점 |
| Spring Mail | Boot 관리 | 이메일 발송 |
| Lombok | Boot 관리 | 보일러플레이트 제거 |
| WebFlux (WebClient) | Boot 관리 | Gemini API 호출용 |

---

## 3. 패키지 구조 & 레이어 규칙

```
src/main/java/com/sku_sku/backend/
├── config/          # @Configuration 빈 (WebClientConfig 등)
├── controller/      # REST 컨트롤러 (아기사자용)
│   └── admin/       # REST 컨트롤러 (운영진 전용, /admin/** 경로)
├── domain/          # JPA @Entity 클래스
│   ├── assignment/  # 과제 관련 엔티티
│   ├── lecture/     # 강의 관련 엔티티
│   └── reviewquiz/  # 복습 퀴즈 관련 엔티티
├── dto/
│   ├── Request/     # 요청 DTO (정적 내부 클래스 패턴)
│   └── Response/    # 응답 DTO (정적 내부 클래스 패턴)
├── email/           # 이메일 서비스
├── enums/           # Enum 정의
├── exception/       # 커스텀 예외 + GlobalExceptionHandler
├── repository/      # Spring Data JPA Repository 인터페이스
├── security/        # Security 설정 (JWT, OAuth2, CORS, Redis)
└── service/         # 비즈니스 로직 서비스
    ├── assignment/  # 과제 서비스
    ├── lecture/     # 강의 서비스
    └── reviewquiz/  # 복습 퀴즈 서비스
```

### 레이어 의존성 규칙
```
Controller → Service → Repository → Domain(Entity)
     ↕            ↕
    DTO          외부 서비스 (S3, Gemini, Redis, Mail)
```

- **Controller**는 Service만 호출. Repository 직접 접근 금지.
- **Service**는 여러 Repository를 조합 가능.
- **Domain(Entity)**에 비즈니스 로직(update 메서드 등) 포함 가능 (Rich Domain).
- **DTO**는 Controller ↔ Service 간 데이터 전달용.

---

## 4. 코딩 컨벤션

### 4.1 엔티티 (Domain)

```java
@Getter
@NoArgsConstructor
@Entity // 한글 주석으로 엔티티 역할 표기
public class 엔티티명 {
    @Id @GeneratedValue
    private Long id; // pk

    // 필드마다 한국어 주석
    private String name; // 사자 이름

    // Enum 필드는 반드시 @Enumerated(EnumType.STRING)
    @Enumerated(EnumType.STRING)
    private TrackType trackType; // 트랙 BACKEND or FRONTEND or DESIGN

    // 날짜 필드 패턴
    private LocalDateTime createDate; // YYYY-MM-DD HH:MM:SS.nnnnnn
    private LocalDateTime updateDate;

    // 생성자 (ID 제외, 필요한 필드만)
    public 엔티티명(필드들...) {
        this.createDate = LocalDateTime.now();
    }

    // update 메서드 (엔티티 내부에 정의)
    public void update(필드들...) {
        this.updateDate = LocalDateTime.now();
    }
}
```

**핵심 규칙:**
- `@Setter` 사용 최소화 (필요한 필드에만 `@Setter` 개별 적용)
- `@AllArgsConstructor` 지양, 필요한 생성자를 직접 정의
- 연관관계: `@ManyToOne(fetch = FetchType.LAZY)` + `@OnDelete(action = OnDeleteAction.CASCADE)`
- 파일 첨부 엔티티: `JoinXxxFile` 네이밍 (예: `JoinLectureFile`, `JoinAssignmentFile`)

### 4.2 DTO

```java
// DTO는 바깥 클래스 안에 정적 내부 클래스로 그룹화
public class AssignmentDTO {
    @Getter @Builder
    public static class CreateAssignment {
        private String title;
        private TrackType trackType;
    }

    @Getter @Builder
    public static class UpdateAssignment { ... }
}
```

**핵심 규칙:**
- Request DTO: `dto/Request/` 패키지
- Response DTO: `dto/Response/` 패키지
- 하나의 도메인에 대해 하나의 외부 DTO 클래스 안에 여러 내부 클래스로 묶기
- Lombok `@Getter`, `@Builder` 활용

### 4.3 컨트롤러

```java
@RestController
@RequiredArgsConstructor
@RequestMapping("/매핑경로")
@Tag(name = "Swagger 태그명")
public class XxxController {

    private final XxxService xxxService;

    @Operation(summary = "(담당자명) API 설명", description = "상세 설명",
            responses = {
                @ApiResponse(responseCode = "200", description = "성공"),
                @ApiResponse(responseCode = "404", description = "실패 사유")
            })
    @GetMapping("/경로")
    public ResponseEntity<응답타입> 메서드명(...) {
        // Service 호출 후 ResponseEntity로 래핑
        return ResponseEntity.status(HttpStatus.OK).body(결과);
    }
}
```

**핵심 규칙:**
- 모든 API에 `@Operation` + `@ApiResponse` Swagger 어노테이션 기재
- `summary`에 `(담당자명)` 접두사 패턴 사용
- 인증이 필요한 API: `@AuthenticationPrincipal Lion lion` 파라미터
- Admin API: `controller/admin/` 패키지, 경로 `/admin/xxx/**`
- 일반 API: `controller/` 패키지, 경로 `/xxx/**`
- 응답은 반드시 `ResponseEntity`로 래핑, 명시적 `HttpStatus` 지정

### 4.4 서비스

```java
@Service
@RequiredArgsConstructor
public class XxxService {
    private final XxxRepository xxxRepository;

    @Transactional
    public void createXxx(...) { ... }

    @Transactional(readOnly = true)
    public XxxResponse getXxx(...) { ... }
}
```

- 조회: `@Transactional(readOnly = true)`, CUD: `@Transactional`
- 예외 처리: 커스텀 예외 throw (예: `InvalidIdException`, `EmptyLionException`)

### 4.5 Repository

```java
public interface XxxRepository extends JpaRepository<Xxx, Long> {
    // 쿼리 메서드명 컨벤션 준수
    Optional<Xxx> findByTitle(String title);
    List<Xxx> findAllByTrackType(TrackType trackType);
}
```

### 4.6 예외 처리

- 커스텀 예외: `exception/` 패키지에 정의
- `GlobalExceptionHandler`에서 `@RestControllerAdvice`로 일괄 처리
- 네이밍: `Invalid___Exception`, `Empty___Exception`, `___FailException`

---

## 5. 도메인 용어 사전

| 한국어 | 영문 코드 | 설명 |
|--------|----------|------|
| 사자 | `Lion` | 학회 회원 (UserDetails 구현체) |
| 아기사자 | `BABY_LION` | 일반 회원 (수강생) |
| 운영진 | `ADMIN_LION` | 관리자 (강의/과제/퀴즈 관리) |
| 트랙 | `TrackType` | BACKEND / FRONTEND / DESIGN |
| 강의 안내물 | `Lecture` | 강의 자료 게시물 |
| 과제 | `Assignment` | 운영진이 출제하는 과제 |
| 제출 과제 | `SubmitAssignment` | 아기사자가 제출한 과제 |
| 피드백 | `Feedback` | 운영진의 과제 피드백 |
| 통과 여부 | `PassNonePass` | PASS / NONE_PASS / UNREVIEWED |
| 복습 퀴즈 주차 | `ReviewWeek` | 주차별 퀴즈 묶음 |
| 복습 퀴즈 | `ReviewQuiz` | 개별 퀴즈 문제 |
| 퀴즈 응답 | `ReviewQuizResponse` | 아기사자의 퀴즈 답변 |
| 정답 여부 | `AnswerStatus` | TRUE / FALSE / EMPTY(주관식 미평가) |
| 문제 유형 | `QuizType` | MULTIPLE_CHOICE / ESSAY_QUESTION |
| 프로젝트 | `Project` | 기수별 프로젝트 갤러리 |
| 캘린더 일정 | `CalendarSchedule` | 학회 일정 |
| 기수 | `classTh` | 예: "13th", "14th" (String으로 관리) |
| 파일 상태 | `FileStatusType` | KEEP / DELETE / NEW (파일 수정 시 사용) |
| 퀴즈 수정 상태 | `UpdateQuizStatus` | CREATE / UPDATE / DELETE / KEEP |

---

## 6. Enum 값 레퍼런스

```java
// 역할
enum RoleType        { ADMIN_LION, BABY_LION }

// 트랙
enum TrackType       { BACKEND, FRONTEND, DESIGN }

// 문제 유형
enum QuizType        { MULTIPLE_CHOICE, ESSAY_QUESTION }

// 과제 통과 여부
enum PassNonePass    { PASS, NONE_PASS, UNREVIEWED }

// 퀴즈 정답 여부
enum AnswerStatus    { TRUE, FALSE, EMPTY }

// 파일 수정 시 상태
enum FileStatusType  { KEEP, DELETE, NEW }

// 퀴즈 수정 시 상태
enum UpdateQuizStatus { CREATE, UPDATE, DELETE, KEEP }

// 허용 파일 타입
enum AllowedFileType {
    JPG, JPEG, PNG, GIF, WEBP,     // 이미지
    PDF, DOCX, XLSX, PPTX, TXT,    // 문서
    ZIP, RAR, SEVEN_Z               // 압축
}
```

---

## 7. 보안 & 인증 구조

### 인증 흐름
```
1. 사용자 → OAuth2 소셜 로그인
2. OAuth2SuccessHandler → JWT 발급 (Access + Refresh)
3. 이후 모든 요청 → JwtAuthenticationFilter에서 토큰 검증
4. @AuthenticationPrincipal Lion lion 으로 현재 사용자 주입
```

### 접근 권한
```java
// 공개 (permitAll)
"/swagger-ui/**", "/v3/api-docs/**", "/api/auth/**"
"/project/all", "/oauth2/redirect"

// ADMIN_LION만 (hasRole)
"/admin/assignment/**", "/admin/schedule/**"
"/admin/lecture/**",    "/admin/project/**"
"/admin/reviewQuiz/**"

// 인증된 사용자 (authenticated)
나머지 모든 요청
```

### CORS 허용 Origin
```
http://localhost:5173    (프론트 개발)
http://localhost:3000    (프론트 개발)
https://sku-sku.com      (프로덕션)
https://legacy.sku-sku.com
```

---

## 8. 파일 업로드 패턴

이 프로젝트는 **Presigned URL** 패턴을 사용합니다:

```
1. 프론트 → POST /s3/presigned (파일명, MIME 타입 전달)
2. 서버 → S3 Presigned PUT URL 발급 (5분 유효) + CDN URL 반환
3. 프론트 → Presigned URL로 S3에 직접 업로드
4. 프론트 → 과제/강의/퀴즈 생성 시 CDN URL + key를 함께 전달
5. 서버 → JoinXxxFile 엔티티에 파일 메타데이터 저장
```

**파일 첨부 엔티티 구조:**
- `fileName` — 원본 파일명
- `fileType` (`AllowedFileType`) — 파일 타입
- `fileSize` (`Long`) — 파일 크기
- `fileUrl` — CloudFront CDN URL
- `fileKey` — S3 저장 경로 (삭제 시 사용)

---

## 9. AI 채점 (Gemini) 사용법

- `GeminiService.evaluateEssayAnswer(question, correctAnswer, userAnswer)`
- WebClient로 Google Gemini API 호출
- 프롬프트: 문제 + 정답 + 사용자 답변을 비교하여 JSON 형태로 결과 반환
- 응답: `{ "result": "TRUE/FALSE", "reason": "판정 이유" }`
- 주관식(`ESSAY_QUESTION`)에만 적용, 객관식은 서버 내부에서 직접 비교

---

## 10. 새 기능 추가 시 체크리스트

새로운 도메인/기능을 추가할 때 아래 순서를 따라주세요:

1. **Enum 정의** → `enums/` 패키지에 필요한 Enum 추가
2. **Entity 생성** → `domain/` 패키지에 JPA 엔티티 생성 (한국어 주석 필수)
3. **Repository 생성** → `repository/` 패키지에 JpaRepository 인터페이스
4. **DTO 생성** → `dto/Request/`, `dto/Response/`에 정적 내부 클래스 패턴
5. **Service 생성** → `service/` 패키지에 비즈니스 로직
6. **Controller 생성** → 일반 API는 `controller/`, 관리자 API는 `controller/admin/`
7. **예외 생성** → 필요 시 `exception/` 패키지에 커스텀 예외 추가
8. **SecurityConfig 업데이트** → 새 Admin 경로 추가 시 `hasRole("ADMIN_LION")` 등록
9. **Swagger 어노테이션** → `@Tag`, `@Operation`, `@ApiResponse` 기재

---

## 11. 자주 쓰는 명령어

```bash
# 빌드
./gradlew build

# 실행
./gradlew bootRun

# 테스트
./gradlew test

# 클린 빌드
./gradlew clean build
```

---

## 12. 설정 파일 주의사항

- `application.yml`은 `.gitignore`에 포함 (`*.yml` 패턴)
- DB, Redis, S3, OAuth2, Gemini 등의 Credentials는 YML에서 환경 변수로 관리
- 설정 변경 시 `@Value` 어노테이션으로 주입받는 클래스 확인 필요:
  - `S3Service` — AWS 인증정보, 버킷명, CloudFront 도메인
  - `GeminiConfig` — Gemini API 키
  - `RedisConfig` — Redis 호스트/포트
  - `JwtUtility` — JWT 시크릿 키
