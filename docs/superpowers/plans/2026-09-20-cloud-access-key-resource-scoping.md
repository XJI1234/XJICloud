# Cloud Access Key Resource Scoping Implementation Plan

> **For agentic workers:** Implement inline in the current dirty `eci-oss` workspace; preserve unrelated user changes and do not commit or push.

**Goal:** Keep one AccessKey catalog while giving `ECI_OPENAPI` and `OSS` independent eligible-key lists and current selections.

**Architecture:** Store resource membership in an element-collection table and store current IDs in one `system_config` row per resource. Keep the existing Admin API prefix, make list/select resource-aware, and continue delegating the final runtime credential write to eci-manager.

**Tech Stack:** Spring Boot 3.3.5, Java 17, Spring Data JPA, H2/PostgreSQL, JUnit 5, Mockito.

**Spec:** `docs/superpowers/specs/2026-09-19-backend-cloud-access-key-catalog-design.md` §0.

## Implementation

- [x] Add the `ECI_OPENAPI | OSS` resource enum and `cloud_access_key_resource` mapping.
- [x] Add resource filtering to the repository and resource fields to request/response DTOs.
- [x] Make list, create, update, delete, select, and saved-key testing enforce resource bindings.
- [x] Change select to apply and persist exactly one requested resource.
- [x] Add an idempotent migration from the legacy global directory/current ID.
- [x] Update focused service, security, serialization, client, migration, and JPA mapping tests.
- [x] Update the design contract and API context documentation.
- [x] Run `cd services/backend && mvn -B clean test`.
- [x] Review the complete diff for correctness, security, regressions, and missing tests.

## Acceptance

- The same credential may appear in both resource lists without duplicating its secret.
- ECI and OSS may select different current IDs.
- Selecting one resource never writes the other resource to 8081.
- Secrets remain absent from all API responses and logs.
- Existing non-test credentials and a valid legacy current ID migrate to both resources.
