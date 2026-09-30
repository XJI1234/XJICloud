# Training Dispatch Push Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Submit an uploaded training job to eci-manager, run the existing mock trainer in a one-shot container, and project the ECI terminal status back to the business job.

**Architecture:** The backend retains Redis as the pending dispatch list and stores dispatch attempt, lease, and ECI task binding on `TrainingJob`. A scheduled consumer claims jobs, posts with a stable `clientRequestId`, retries ambiguous outcomes, and polls bound tasks. Eci-manager deduplicates task creation by that key. The container reads presigned input URLs and writes its output through the existing `storage.mode=keys` contract.

**Tech Stack:** Java 17, Spring Boot 3.3.5, Spring Data JPA/Redis, Maven, Python 3, requests.

**Spec:** `docs/superpowers/specs/2026-09-23-training-dispatch-push-architecture-design.md`

## Global Constraints

- Keep changes scoped to `services/backend/`, `eci-manager/`, `services/gpu-worker/`, their relevant documentation, and the legacy worker deployment entry if necessary.
- Preserve Redis usage for authentication and the pending training list. Do not add a second queue store or migrate PLY/SPZ to OSS.
- Preserve the existing ECI create/delete lifecycle and `storage.mode=keys` behavior.
- No real cloud calls in automated tests. Do not use secrets in source, logs, or responses.
- Do not commit or push automatically. Preserve pre-existing untracked files.
- The first-version user status remains coarse: `QUEUED`, `RUNNING`, `COMPLETED`, `FAILED`, `CANCELLED`.

## Task 1: ECI task idempotency (strict TDD)

**Scope:** `eci-manager/src/main/java/com/xjicloud/eci/task/` and focused tests/docs.

- [ ] Add a failing API/service test: repeated `POST /api/v1/eci/tasks` with one `clientRequestId` returns the same task and one instance; distinct keys create distinct tasks.
- [ ] Add a failing concurrency test for simultaneous requests with one key, and a test that legacy requests without a key still create separate tasks.
- [ ] Add a nullable, uniquely constrained task request ID, request DTO field, and transactional conflict recovery; do not change the existing instance ClientToken.
- [ ] Run focused tests, then `mvn test` in `eci-manager/`; update the ECI API documentation.

## Task 2: Backend durable dispatch and reconciliation (strict TDD)

**Scope:** `services/backend/src/main/java/com/xjicloud/job/`, `queue/`, focused config/client additions, and focused tests.

- [ ] Write failing tests for queue claim, HTTP timeout retry with stable key, one binding, retry using a new attempt key, and recovery after a popped item or process interruption.
- [ ] Add persisted dispatch fields and conditional DB transitions. Redis is the normal pending consumer; DB scanning repairs missing list entries or expired claims. Never keep a DB transaction open during an ECI HTTP call.
- [ ] Send `storage.mode=keys` with dataset asset OSS keys and the job output key. Configure the training image and request timeouts through existing `xjicloud.eci-manager` configuration; missing image/key must keep the job retryable and visible as `QUEUED`.
- [ ] Bind only the matching active attempt. If a cancellation races with a successful POST, bind then cancel the returned ECI task. Retry cancellation until ECI confirms terminal state.
- [ ] Poll bound, nonterminal ECI tasks and map `SUCCEEDED`/`FAILED`/`CANCELLED` to business status and SSE. Do not overwrite terminal/cancelled/new-attempt jobs with stale poll results. Preserve existing output key and download URL contract.
- [ ] Disable legacy Worker queue consumption and job callbacks as an execution path, preserving code for reference where practical. Keep unrelated Redis consumers intact.
- [ ] Run focused tests, then `mvn -B test` in `services/backend/`.

## Task 3: One-shot training container (strict TDD)

**Scope:** `services/gpu-worker/` and focused deployment/docs changes.

- [ ] Write a failing local test for the one-shot entry: parse `XJI_INPUT_FILES_JSON`, download images, invoke existing mock trainer, upload to `XJI_OUTPUT_UPLOAD_URL`, and exit successfully without backend registration or callbacks.
- [ ] Add the minimal entry script and make the image launch it for ECI tasks. Preserve legacy Worker code, but ensure the default deployment cannot consume the same Redis queue.
- [ ] Run the focused Python test. Document image build/push and the required backend image setting; do not trigger a real cloud build or resource creation.

## Task 4: Integration and review

- [ ] Run full Maven tests in both Java modules and the focused Python test; perform a local Mock ECI dispatch flow if runtime prerequisites are available.
- [ ] Inspect the combined diff for duplicate ECI creation, lost Redis claims, cancellation races, output URL expiry/visibility, credential leaks, and existing API regressions. Fix High/Critical issues.
- [ ] Update `docs/AGENT_CONTEXT.md`, `eci-manager/AGENT_CONTEXT.md`, and applicable API/operator documentation for the new route and configuration.
- [ ] Report exact verification, manual user path or E2E gap, and Code Review findings.
