---
name: frontend-ddd
description: Frontend Domain-Driven Design and layered architecture for decoupling business logic from UI. Use when designing or refactoring frontend modules, splitting views from domain logic, introducing repository/mapper/adapter layers, anti-corruption layers against API DTOs, bounded contexts, aggregate testing, or migrating legacy React/Vue code toward Clean Architecture / DDD patterns.
---

# Frontend DDD

Guide for applying **Domain-Driven Design (DDD)** and **Clean Architecture** on the frontend. Synthesized from industry practice (奇舞精选《前端领域的"干净架构"》、SegmentFault 文博《DDD 在前端领域的思考和落地》、阿里云 DDD 分层实践、Eric Evans / Vernon 经典分层与六边形架构、Tomasz Ducin 防腐层文章，以及 Nx / React / Vue 开源落地案例).

> **Note on source article:** The user-requested WeChat link `https://mp.weixin.qq.com/s/GTqx41YYYV_WgTl8YRCQTw` was blocked by environment verification at research time. This skill incorporates equivalent publicly accessible material on the same topic. See [reference.md](reference.md) for full bibliography.

## Core thesis

Frontend complexity comes from **mixing three concerns**:

1. **Data production** — fetch, map, validate, normalize API payloads
2. **Runtime orchestration** — state, side effects, cross-module coordination
3. **View consumption** — render, interact, route

DDD on the frontend does **not** mean copying backend microservice DDD verbatim. It means:

- **Stable domain layer** survives framework swaps (Vue → React) and new surfaces (mobile, embed)
- **Thin view layer** only consumes ready-to-render models; no `filter`, no field math, no API shape knowledge
- **Dependency inward** — UI and infrastructure adapt to domain, never the reverse
- **Bounded contexts** — same word ("User", "Project") may mean different things in different features

```
External API / Storage
        ↓  (DTO)
   Infrastructure — HTTP, localStorage, SDK
        ↓  (mapped entity)
   Repository + Mapper  ← Anti-Corruption Layer
        ↓  (domain entity / value object)
   Domain — rules, invariants, calculations
        ↓  (use case result)
   Application / Adapter — orchestration for a screen
        ↓  (view model, already safe)
   Presentation — Vue/React components, stores as UI state only
```

## When to apply

| Signal | Action |
|--------|--------|
| Business rules duplicated across views | Extract domain services |
| API field renames break many components | Add mapper + local model (ACL) |
| Components >300 LOC mixing fetch + UI + validation | Split layers |
| Same domain reused in web + admin + embed | Domain package, multiple adapters |
| Form/permission/pricing rules are non-trivial | Domain entities + invariants |
| Simple CRUD landing page, 3 screens | **Skip full DDD** — use `api/` + composables |

**Rule of thumb:** Apply when domain logic is **stable, complex, and reused** — not because the pattern is fashionable. Prefer **4-layer golden model** over 12-layer over-engineering.

## Strategic design (frontend adaptation)

### Ubiquitous Language (UL)

Team-shared vocabulary in code names, folder names, and docs. A `Project` in **training** context ≠ `Project` in **billing** context unless explicitly shared.

### Bounded Context (BC)

Each BC is a cohesive module with its own models. In monorepos:

```
src/features/{context}/     # or packages/domain-{context}/
  domain/
  application/
  infrastructure/
  presentation/
```

Align BC boundaries with **business capabilities**, not pages. One page may compose multiple BCs via application layer — never import another BC's infrastructure directly.

### Context Map (cross-BC integration)

- **Shared Kernel** — small shared types/utils (use sparingly)
- **Customer-Supplier** — upstream API owner vs frontend consumer → ACL on consumer side
- **Anti-Corruption Layer** — mandatory at every external boundary (REST, MFE pub/sub, third-party SDK)

## Tactical design

| Element | Frontend role | Example |
|---------|---------------|---------|
| **Entity** | Object with identity + lifecycle | `TrainingJob`, `PointCloudDataset` |
| **Value Object** | Immutable, compared by value | `Money`, `GeoBounds`, `FileSize` |
| **Aggregate Root** | Consistency boundary; external refs by ID only | `Project` owns `Dataset[]` mutations |
| **Domain Service** | Logic spanning entities | `CanStartTraining(project, quota)` |
| **Repository (interface)** | Domain-owned port for persistence | `ProjectRepository.findById(id)` |
| **Repository (impl)** | Infrastructure; calls HTTP + mapper | `HttpProjectRepository` |
| **Domain Event** | Notify other BCs (RxJS, mitt, store) | `ProjectCreated`, `UploadCompleted` |
| **Factory** | Complex entity construction | `Project.fromUploadManifest()` |

### Invariants → tests

Every aggregate invariant becomes an explicit assertion — the **load-bearing layer** between unit tests and E2E:

```typescript
// domain/project/trainingJob.ts
export function assertCanSubmit(job: TrainingJob, quota: Quota): void {
  if (job.status !== 'draft') throw new DomainError('JOB_NOT_DRAFT')
  if (quota.remaining <= 0) throw new DomainError('QUOTA_EXCEEDED')
}
```

Do **not** mock inside aggregate tests. Mock only external ports (HTTP, clock, storage).

## Recommended 4-layer model (frontend)

Consolidated from Clean Architecture + DDD frontend practice — the **default** for this repo unless requirements demand more.

| Layer | Responsibility | Depends on | Must NOT |
|-------|----------------|------------|----------|
| **Presentation** | Components, layout, UI state, routing | Application, read-only domain types | Call `fetch`, know DTO shape, encode business rules |
| **Application** | Use cases per screen/feature; coordinates domain + ports | Domain, port interfaces | Import Vue/React in domain packages |
| **Domain** | Entities, VOs, domain services, invariants, repository **interfaces** | Nothing external | HTTP, localStorage, framework APIs |
| **Infrastructure** | HTTP clients, mappers, repository **implementations**, cache | Domain interfaces | Leak DTOs above repository boundary |

### Data flow (mandatory direction)

```
View → Application/Adapter → Repository → Service(HTTP) → API
                ↓                ↓
             Domain          Mapper (ACL)
```

Return type at adapter boundary: **`Result<T, E>`** (tuple or Result pattern) — never throw into UI without mapping.

```typescript
type Result<T, E = DomainError> = readonly [error: E | null, data: T | null]

export async function getProjectDetail(params: { id: string }): Promise<Result<Project>> {
  const [error, dto] = await projectRepository.findById(params.id)
  if (error) return [error, null]
  return [null, dto] // already Project entity, not raw API
}
```

## Directory conventions

### Per bounded context (feature-first)

```
features/project/
├── domain/
│   ├── entities/project.entity.ts
│   ├── value-objects/dataset-status.vo.ts
│   ├── services/project-policy.service.ts
│   └── repositories/project.repository.ts   # interface only
├── application/
│   └── use-cases/load-project-list.usecase.ts
├── infrastructure/
│   ├── mappers/project.mapper.ts            # DTO → Entity (ACL)
│   ├── repositories/http-project.repository.ts
│   └── services/project-api.service.ts      # raw HTTP / SDK
└── presentation/
    ├── composables/useProjectList.ts        # wires use case to Vue
    └── components/ProjectCard.vue
```

### Mapping to XJICloud (incremental)

Current layout (`apps/web/src/api`, `stores`, `views`) can evolve without big-bang rewrite:

| Current | Target layer | Migration |
|---------|--------------|-----------|
| `api/*.ts` | `infrastructure/services` + keep thin client | Move mapping out of api files |
| `stores/*.ts` | `presentation` UI state OR `application` if orchestrating | Strip business rules to domain |
| `views/*.vue` | `presentation` | Remove data transforms from template/script |
| `types/*.ts` | Split: DTO in infra, Entity in domain | Never one shared `any` blob |
| `utils/*.ts` | `shared` only if domain-agnostic | Domain utils → domain layer |

## Layer rules (enforce in code review)

1. **Presentation consumes, never produces domain data** — no `price * 0.9` in template; no `response.data.list.map(...)` in component
2. **Mapper owns all field normalization** — dates, dict labels, `null` defaults, list key `records`, status key `state`, magic numbers
3. **Repository returns domain entities** — on error, return safe default entity (empty list, empty aggregate), not `undefined` cascade
4. **Application layer = use case scripts** — "load project list", "submit training job"; flat, testable functions
5. **Domain has zero framework imports** — pure TypeScript; enables Vitest without `@vue/test-utils`
6. **Dependency rule** — outer → inner only; use DI (Vue `provide/inject`, factory params) for repository implementations

## Anti-Corruption Layer (ACL)

Place ACL at **every** boundary you don't own:

- REST/GraphQL response → local `*Model` / `*Entity`
- Micro-frontend pub/sub payloads → local event types
- Generated OpenAPI types → never import in `.vue` files

```typescript
// infrastructure/mappers/project.mapper.ts
import type { ProjectDto } from '../dto/project.dto'
import { ProjectEntity } from '../../domain/entities/project.entity'

export function mapProjectFromDto(dto: ProjectDto): ProjectEntity {
  return new ProjectEntity({
    id: dto.id ?? '',
    name: dto.project_name ?? '',           // backend snake_case isolated here
    status: mapStatus(dto.state ?? -1),     // default -1 for unknown state
    updatedAt: formatDate(dto.updated_at),  // YYYYMMDD → display string
  })
}
```

**Incremental adoption:** On next API change, introduce local model + mapper; switch callers one file at a time. Use ESLint `no-restricted-imports` to block DTO imports in `presentation/`.

## View vs domain separation

| Stable (domain) | Unstable (presentation) |
|-----------------|-------------------------|
| Pricing / permission rules | Layout and CSS |
| Field semantics and validation | Route structure |
| API protocol (via ACL) | Component library |
| Repository contracts | Page-specific loading UX |

**ViewModel split (when needed):**

- **Stable:** `Service` → `Repository` → `Model/Entity`
- **Unstable:** `View` → `Composable/Hook` → calls Application adapter

Avoid fat ViewModels that duplicate domain logic. Prefer: composable calls use case, exposes readonly refs.

## State management placement

| Store content | Layer |
|---------------|-------|
| Server-derived business state | Prefer domain + application; cache in repository |
| UI-only: modal open, tab index, form draft | Presentation store (Pinia) |
| Cross-route session | Application or shared infrastructure |

Pinia/Zustand/Redux are **not** domain layers. They hold UI state or act as **application-level caches** — business rules stay in domain services.

## Testing pyramid (frontend DDD)

| Layer | Test type | Focus |
|-------|-----------|-------|
| Domain | Unit | Invariants, entity methods, domain services |
| Application | Integration | Use case with mocked repositories |
| Infrastructure | Contract | Mapper snapshots, API fixture → entity |
| Presentation | Component | Renders given view model; no API mock |
| E2E | Few | Critical user journeys only |

Aggregate-root tests are the **missing middle** — invest here before adding E2E.

## Anti-patterns

| Smell | Fix |
|-------|-----|
| **Anemic domain** — entities are `{ id, name }` bags, logic in services everywhere | Move behavior onto entities where natural |
| **God class / god component** — 1000 LOC `User` used everywhere | Split by BC; ACL per module |
| **DTO leak** — OpenAPI types in 20 components | Mapper at repository; ESLint ban |
| **Logic in template** — `\| filter`, inline `map`, formatting | Precompute in mapper/adapter |
| **Store as junk drawer** — fetch + transform + UI in one Pinia module | Split infrastructure vs presentation |
| **Over-layering** — 12 folders for login page | Start with api + composable; extract when pain appears |
| **Cross-BC import** — `features/order` imports `features/catalog/infrastructure` | Compose via application facade or domain event |

## Migration: Strangler pattern

Never big-bang rewrite.

1. **New features** — full layered structure under `features/{name}/`
2. **Touched legacy files** — extract mapper when editing an `api/` call; leave rest unchanged
3. **Feature flags** — route new use case vs old path during transition
4. **Measure** — track duplicate business logic count, components importing `api/` directly, test coverage on domain

## Agent workflow checklist

When asked to design, refactor, or review frontend architecture:

```
- [ ] 1. Identify bounded context(s) — name the UL terms
- [ ] 2. List use cases (actor + action + outcome)
- [ ] 3. Sketch entities, VOs, aggregate roots
- [ ] 4. Define repository ports (interfaces) in domain
- [ ] 5. Place mappers in infrastructure (ACL)
- [ ] 6. Implement use cases in application layer
- [ ] 7. Wire presentation via composables — thin views
- [ ] 8. Add domain unit tests for invariants
- [ ] 9. Verify dependency direction (no inward imports of Vue/fetch in domain)
- [ ] 10. Confirm scope — reject over-engineering for trivial CRUD
```

## Minimal example (TypeScript, framework-agnostic domain)

```typescript
// domain/entities/project.entity.ts
export class ProjectEntity {
  constructor(readonly id: string, readonly name: string, readonly status: ProjectStatus) {}
  canStartTraining(): boolean {
    return this.status === 'ready'
  }
}

// domain/repositories/project.repository.ts
export interface ProjectRepository {
  findById(id: string): Promise<Result<ProjectEntity>>
}

// application/load-project.usecase.ts
export async function loadProject(repo: ProjectRepository, id: string): Promise<Result<ProjectViewModel>> {
  const [err, project] = await repo.findById(id)
  if (err || !project) return [err ?? new DomainError('NOT_FOUND'), null]
  return [null, { id: project.id, title: project.name, canTrain: project.canStartTraining() }]
}

// presentation/composables/useProject.ts (Vue)
export function useProject(id: string) {
  const state = ref<ProjectViewModel | null>(null)
  onMounted(async () => {
    const [, vm] = await loadProject(projectRepository, id)
    state.value = vm
  })
  return { state }
}
```

## Quick reference: Clean Architecture ↔ DDD ↔ MVC

| Clean Architecture | DDD (Evans 4-layer) | Frontend MVC analogy |
|--------------------|---------------------|----------------------|
| Entities | Domain | Model (rich) |
| Use Cases | Application | Controller (thin) |
| Interface Adapters | Infrastructure + ACL | — |
| Frameworks & Drivers | Presentation | View |

## Further reading

Detailed patterns, source links, DCI/five-layer/hexagonal variants, monorepo Nx layout, and field-normalization cookbook: [reference.md](reference.md)
