---
name: sku-sku-backend
description: Use when working inside the SKUSKU_Back Spring Boot repository to debug runtime, build, auth, Redis, S3, mail, or Gemini issues, or to implement features while following this repo's controller-service-repository and DTO conventions. Do not use for unrelated generic Java or Spring tasks outside this repository.
---

# SKUSKU_Back Maintainer

Use this skill when the task is specifically about the `SKUSKU_Back` repository.

## Quick Start

1. Run the diagnosis script before major edits or when an error report is vague:

   `powershell -ExecutionPolicy Bypass -File .agents\skills\sku-sku-backend\scripts\diagnose.ps1 -Mode summary`

2. If the task is mainly about startup or configuration failures, read `references/env-and-debug.md`.

3. Map the affected behavior through the owning flow before editing:
   - controller
   - service
   - repository/entity
   - integration such as Redis, S3, mail, or Gemini

4. Validate with the smallest safe command first. This repo often needs external config to boot.

## Repo-Specific Workflow

- Treat `AGENTS.md` plus the actual Java sources as the source of truth.
- Preserve the split between:
  - `controller/` for member-facing APIs
  - `controller/admin/` for admin APIs
- Preserve the DTO pattern:
  - request DTOs under `dto/Request`
  - response DTOs under `dto/Response`
  - nested static DTO classes are common and intentional
- When attachments are involved, inspect both the join-file table and S3 cleanup path before changing logic.
- Do not assume a startup failure is a code bug until config keys and local services are checked.

## Symptom Routing

- `401`, `403`, login redirect loops, expired-cookie behavior:
  - inspect `security/`
  - inspect `service/OAuth2Service.java`
- File upload or deletion issues:
  - inspect `service/S3Service.java`
  - inspect the relevant `Join*FileService`
  - inspect the parent service that triggers cleanup
- Review quiz grading issues:
  - inspect `service/reviewquiz/ReviewQuizService.java`
  - inspect `service/GeminiService.java`
  - inspect `security/GeminiConfig.java`
- Assignment submission or feedback issues:
  - inspect `service/assignment/AssignmentService.java`
  - inspect related repositories and join-file services
- Calendar, lecture, or project CRUD issues:
  - inspect the paired controller, service, repository, and entity together

## Validation

- Fast environment snapshot:
  - `powershell -ExecutionPolicy Bypass -File .agents\skills\sku-sku-backend\scripts\diagnose.ps1 -Mode summary`
- Config-focused view:
  - `powershell -ExecutionPolicy Bypass -File .agents\skills\sku-sku-backend\scripts\diagnose.ps1 -Mode config`
- Attempt Gradle validation:
  - `powershell -ExecutionPolicy Bypass -File .agents\skills\sku-sku-backend\scripts\diagnose.ps1 -Mode validate`

## When To Load References

- Read `references/env-and-debug.md` when you need:
  - environment key families
  - likely causes of boot failure
  - auth and file-flow troubleshooting hints
  - a quick map of public and admin endpoint groups
