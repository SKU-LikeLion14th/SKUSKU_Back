# AGENTS.md

## Purpose

This repository-level instruction file gives Codex project-specific guidance for `SKUSKU_Back`.
Treat the source code as the source of truth and use `WALKTHROUGH.md` as a helpful overview, not as a substitute for reading the affected code.

## Project Snapshot

- Stack: Java 21, Spring Boot 3.4.3, Gradle, Spring Data JPA, Spring Security, OAuth2 login, JWT, Redis, AWS S3, CloudFront, Spring Mail, WebClient, Google Gemini.
- Main package: `src/main/java/com/sku_sku/backend`
- Main entrypoint: `src/main/java/com/sku_sku/backend/SkuSkuApplication.java`
- Key docs:
  - `WALKTHROUGH.md`: architecture and domain overview
  - `README.md`: recent notable changes
  - `AGENT.md`: older internal guide; not automatically discovered by Codex

## Working Agreements

- Make small, targeted changes that preserve the current package boundaries and naming style.
- Before editing a feature, inspect the full path that owns the behavior:
  - controller
  - service
  - repository/entity
  - external integration such as Redis, S3, mail, or Gemini
- Do not invent configuration keys. This repo does not commit runtime secrets, so startup failures are often environment issues rather than code issues.
- Prefer validating with the smallest safe command first. Full application startup may fail without local secrets or infrastructure.
- When behavior depends on uploaded files, always reason about both database records and S3 object cleanup.

## Commands

Use Windows-friendly commands first because this workspace is typically opened from PowerShell.

- Run tests: `.\gradlew.bat test`
- Run one test class: `.\gradlew.bat test --tests com.sku_sku.backend.SkuSkuApplicationTests`
- Start app: `.\gradlew.bat bootRun`
- Inspect repository skill: `Get-Content .agents\skills\sku-sku-backend\SKILL.md`
- Run repo diagnosis script: `powershell -ExecutionPolicy Bypass -File .agents\skills\sku-sku-backend\scripts\diagnose.ps1 -Mode summary`

## Environment And Infra Expectations

The repository does not currently include committed `application.yml` or `application.properties` files. Expect local or secret-managed values for at least the following:

- `jwt.base64Secret`
- `cookie.secure`
- `cookie.sameSite`
- `cloud.aws.cloudfront`
- `cloud.aws.credentials.access-key`
- `cloud.aws.credentials.secret-key`
- `cloud.aws.s3`
- `gemini.api.*`
- Spring-managed infrastructure families such as:
  - `spring.datasource.*`
  - `spring.data.redis.*`
  - `spring.mail.*`
  - `spring.security.oauth2.client.*`

Current code assumptions to remember:

- `RedisConfig` creates a default `LettuceConnectionFactory`, so local defaults point to `localhost:6379` unless overridden by Spring config.
- `S3Service` constructs `S3Client` and `S3Presigner` directly from injected AWS keys.
- `JwtUtility` requires `jwt.base64Secret` at construction time.
- `GeminiConfig` binds from `gemini.api`.

## Architecture Map

- `controller/`: user-facing REST APIs
- `controller/admin/`: admin-only REST APIs under `/admin/**`
- `service/`: business logic and orchestration
- `repository/`: Spring Data JPA repositories
- `domain/`: JPA entities
- `dto/Request` and `dto/Response`: split request/response DTOs, often with nested static classes
- `security/`: JWT, OAuth2, Redis-backed auth request storage, CORS
- `exception/`: custom exceptions and global exception handler

## Important Domain Areas

- `Lion`: authenticated user principal and role holder
- `Lecture` + `JoinLectureFile`: lecture content and attachments
- `Assignment`, `SubmitAssignment`, `Feedback`, `JoinAssignmentFile`, `JoinSubmitAssignmentFile`: assignment workflow
- `ReviewWeek`, `ReviewQuiz`, `ReviewQuizResponse`, `AnswerChoice`, `JoinReviewQuizFile`: quiz workflow
- `Project`: public project gallery entries
- `CalendarSchedule`: schedule/calendar items

## Security And Auth Notes

- OAuth2 login uses a custom authorization request resolver and a Redis-backed authorization request repository.
- Frontend redirect targets are stored in Redis under `oauth2_redirect:{state}` while Spring Security keeps ownership of the actual OAuth `state` value.
- Successful login sets an HttpOnly `access_token` cookie.
- Refresh-token-like behavior is Redis-backed and currently only issued for `ADMIN_LION`.
- Public paths currently include Swagger, `/v3/api-docs/**`, `/api/auth/**`, `/oauth2/redirect`, and `/project/all`.
- If you change auth behavior, inspect these files together:
  - `security/SecurityConfig.java`
  - `security/JwtAuthenticationFilter.java`
  - `security/JwtUtility.java`
  - `security/OAuth2SuccessHandler.java`
  - `security/CustomAuthorizationRequestResolver.java`
  - `security/RedisOAuth2AuthorizationRequestRepository.java`
  - `service/OAuth2Service.java`

## Feature-Specific Guidance

### Controllers

- Keep Swagger annotations in place.
- Return `ResponseEntity` explicitly.
- Preserve the split between general user controllers and admin controllers.

### Services

- Most services are class-level `@Transactional(readOnly = true)` with method-level write transactions.
- Preserve side effects such as:
  - deleting S3 objects when removing attachment records
  - cleaning join-file records alongside parent entities
  - sending mail after assignment feedback changes

### Entities And Enums

- Existing entities usually own timestamp updates in domain methods such as `update(...)`.
- Enums are expected to be stored as strings where `@Enumerated(EnumType.STRING)` is already used.
- Keep naming consistent with the existing domain vocabulary: Lion, ReviewWeek, JoinXxxFile, etc.

### Quizzes And Gemini

- Objective quizzes are graded locally.
- Essay quizzes call `GeminiService`.
- If quiz scoring changes, inspect both `service/reviewquiz/ReviewQuizService.java` and `service/GeminiService.java`.

## Validation Strategy

- If config or infra is missing, prefer:
  - targeted source inspection
  - repository diagnosis script
  - narrow test commands
- If you can run tests, use `.\gradlew.bat test`.
- If startup/test validation is blocked by missing properties, Redis, MySQL, OAuth credentials, mail, or Gemini configuration, report that clearly instead of guessing.

## Repo Skill

This repository includes a local skill at `.agents/skills/sku-sku-backend`.
Use it for debugging, environment triage, and faster onboarding when working in this codebase.
