# Environment And Debug Notes

## Runtime Config Families

This repository depends on local or secret-managed configuration that is not committed to Git.

Directly referenced from code:

- `jwt.base64Secret`
- `cookie.secure`
- `cookie.sameSite`
- `cloud.aws.cloudfront`
- `cloud.aws.credentials.access-key`
- `cloud.aws.credentials.secret-key`
- `cloud.aws.s3`
- `gemini.api.key`
- `gemini.api.model`
- `gemini.api.project`
- `gemini.api.location`
- `gemini.api.maxTokens`
- `gemini.api.temperature`
- `gemini.api.topK`
- `gemini.api.topP`

Very likely required through Spring Boot integrations:

- `spring.datasource.*`
- `spring.data.redis.*`
- `spring.mail.*`
- `spring.security.oauth2.client.*`

## Common Failure Patterns

### Application fails during boot

Likely causes:

- missing `application*.yml`, `application*.yaml`, or `application*.properties`
- missing `jwt.base64Secret`
- Redis unavailable while auth components initialize
- datasource or OAuth client settings missing

First move:

- run the diagnosis script in `summary` or `config` mode

### Gradle fails before tests even start

Likely causes:

- JDK/toolchain mismatch
- shell `java` and Gradle are resolving different Java installations

Remember:

- `build.gradle` targets Java 21
- compare the diagnosis script output with the JDK Gradle actually uses
- if Gradle is using a different major version, align `JAVA_HOME` or the local toolchain first

### Login succeeds but redirects incorrectly

Inspect:

- `security/CustomAuthorizationRequestResolver.java`
- `security/OAuth2SuccessHandler.java`
- `security/RedisOAuth2AuthorizationRequestRepository.java`

Remember:

- redirect targets are stored in Redis with `oauth2_redirect:{state}`
- the actual OAuth `state` is still Spring Security's value

### Admin endpoint returns unauthorized after cookie expiry

Inspect:

- `security/JwtAuthenticationFilter.java`
- `service/OAuth2Service.java`
- Redis key `refresh:{email}`

Remember:

- refresh behavior is only issued for `ADMIN_LION`

### File metadata looks right but S3 objects remain, or vice versa

Inspect both:

- the relevant `Join*File` repository/service
- `service/S3Service.java`

Common rule:

- delete database rows and S3 keys together

### Essay quiz grading is unstable

Inspect:

- `service/reviewquiz/ReviewQuizService.java`
- `service/GeminiService.java`
- `security/GeminiConfig.java`

Remember:

- Gemini parse failures fall back to conservative grading behavior

## Endpoint Groups

General member-facing groups:

- `/assignment/**`
- `/lecture/**`
- `/project/**`
- `/log/**`
- `/reviewWeek/**`
- `/reviewQuiz/**`
- `/schedules`
- `/s3/**`

Admin-facing groups:

- `/admin/assignment/**`
- `/admin/lecture/**`
- `/admin/project/**`
- `/admin/reviewQuiz/**`
- `/admin/schedule/**`

Public groups:

- `/project/all`
- `/swagger-ui/**`
- `/v3/api-docs/**`
- `/oauth2/redirect`

## Safe Validation Order

1. Diagnosis script
2. Targeted source inspection
3. Narrow Gradle test command
4. Full `.\gradlew.bat test`
5. `.\gradlew.bat bootRun` only when config and infra are ready
