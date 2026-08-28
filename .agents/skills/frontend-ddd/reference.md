# Frontend DDD — Reference & Sources

Extended material for the `frontend-ddd` skill. Read when implementing non-trivial architecture, training the team, or choosing between 4-layer / 5-layer / hexagonal variants.

## Primary sources (research bibliography)

### Target article (blocked at fetch time)

| Item | URL | Status |
|------|-----|--------|
| User-requested WeChat article | https://mp.weixin.qq.com/s/GTqx41YYYV_WgTl8YRCQTw | Environment verification blocked automated access |

Content below substitutes equivalent publicly accessible articles on frontend DDD / Clean Architecture.

### WeChat / Chinese community

| Title | Author / Source | URL | Key contribution |
|-------|-----------------|-----|------------------|
| 前端领域的「干净架构」 | ConardLi / 奇舞精选 (translated from bespoyasov) | https://mp.weixin.qq.com/s/sX7QzQ_NgOSzhgCQv4ZN_A | Domain / Application / Adapter 三层；依赖规则；商店应用完整示例；成本与取舍 |
| DDD 在前端应用中的一些思考 | — | https://mp.weixin.qq.com/s/pKBI5N76kA-NOVe52sz7MA | ViewModel vs 直接依赖；稳定层 vs 不稳定层划分 |
| 还在搞三层架构？了解下 DDD 分层架构的三种模式吧 | 芋道源码 | https://mp.weixin.qq.com/s/nFPNYt35XdoGs5nA-2KBIQ | 四层 / 五层(DCI) / 六边形；严格 vs 松散分层 |
| 领域驱动设计和 CQRS 落地 | — | https://mp.weixin.qq.com/s/R-jBnPhWJHs7J-4CETV88A | DIP 改进分层；Command/Query 分离；抽象在内、实现在外 |
| 使用 DDD 设计前端分层架构实现视图与逻辑解耦 | 阿里云开发者社区 | https://developer.aliyun.com/article/1480784 | 应用核心代码 vs 组件；models/server/applications 目录；Zustand 实践 |

### English / open source

| Title | URL | Key contribution |
|-------|-----|------------------|
| DDD 在前端领域的思考和落地 (SegmentFault) | https://segmentfault.com/a/1190000046906392 | pet 领域分层；Repository/Mapper/Adapter/Service；Result Pattern；Monorepo 工程化 |
| Reference implementation | https://github.com/cklwblove/domain-front | 上述文章的落地代码 |
| Clean Architecture on Frontend (original) | https://dev.to/bespoyasov/clean-architecture-on-frontend-4311 | 干净架构前端原文 |
| Example repo | https://github.com/bespoyasov/frontend-clean-architecture | React 商店示例 |
| Anti-Corruption Layer in Frontend | https://ducin.dev/anti-corruption-layer-in-frontend-development | ACL 动机、渐进式引入、MFE 边界 |
| Frontend with DDD (Nx) | https://darkyzhou.net/articles/frontend-with-ddd-3 | feature/ui/domain/data-access library 划分 |
| React DDD Hexagonal | https://github.com/juliomatcom/react-vite-typescript-ddd-hexagonal | 四层 + 六边形依赖方向 |
| React Clean Architecture | https://github.com/schorts99/React-Clean-Architecture | 模块化 + CQRS + 严格依赖规则 |
| Framework-Agnostic DDD Frontend | https://www.boundev.com/blog/framework-agnostic-domain-driven-frontend-2026 | BC 划分、DI via Context |

## Problem → goal mapping

From SegmentFault 文博实践与社区共识：

| Problem | DDD response |
|---------|--------------|
| 视图层过重、分层模糊 | 视图只做消费；逻辑下沉 domain/application |
| 相同逻辑多处复制 | 领域服务 + 单一 mapper |
| 后端字段变更波及 UI | ACL (mapper) 隔离 |
| 团队知识不同步 | 限界上下文 + 统一语言 |
| 框架迁移成本高 | 领域层框架无关 |
| C 端 UI 个性化、难复用 | 领域层跨页面复用；UI 层保持薄 |

## Architecture variants

### Variant A: Clean Architecture 3-layer (frontend default mental model)

```
Adapter (UI, HTTP, storage)
    ↓ depends on
Application (use cases, ports)
    ↓ depends on
Domain (entities, domain services)
```

**Dependency rule:** only outer → inner. Domain never imports adapters.

### Variant B: Evans DDD 4-layer (backend-aligned)

| Layer | Frontend mapping |
|-------|------------------|
| User Interface | Vue/React views, layouts |
| Application | Use cases, composables orchestrating |
| Domain | Entities, VOs, domain services, repo interfaces |
| Infrastructure | HTTP, mappers, repo impl, Pinia persistence |

Traditional 4-layer uses **loose layering** for Infrastructure (any upper layer may use it). Prefer **DIP**: infrastructure implements domain ports.

### Variant C: SegmentFault 5-sublayer domain module

Per bounded context module:

```
{context}/
├── adapter/      # 主防腐层 — 视图调用的统一入口
├── repository/   # 次防腐层 — 数据访问 + mapper/
│   └── mapper/
├── service/      # HTTP / storage，Result Pattern
└── model/        # Entity — 前端领域对象 (FDO)
```

Data flow:

```
Backend DTO → service → repository(mapper) → Entity → adapter → View
```

### Variant D: Aliyun React practice (Zustand)

```
dataArea/{page}/
├── models/        # 业务状态模型 (zustand store shape)
├── server/        # 底层数据获取，写回 model
└── applications/  # 用例编排，供 View 调用 init/actions
pages/UI/          # 纯消费 + 触发 application
```

View rules:

- Call `application.init()` / actions — not `server` directly
- Subscribe model via selector — no inline transform

### Variant E: Nx DDD libraries

Per bounded context:

| Library type | Role |
|--------------|------|
| `domain` | Entities, validation, facade exposing Observables |
| `data-access` | HTTP to backend |
| `feature` | Smart components (use cases wired) |
| `ui` | Dumb presentational components |
| `util` | Context-agnostic helpers |

Cross-BC events: pub/sub or shared event bus — not direct domain imports.

### Variant F: DCI 5-layer (advanced)

When behavior varies heavily by scenario (same entity, many roles):

- **Data** — entity structure
- **Interaction (role)** — scenario-specific behavior
- **Context** — casts entity to role for one use case

Rarely needed on typical CRUD frontends. Consider for workflow-heavy control planes.

### Variant G: Hexagonal (ports & adapters)

Domain at center. All I/O through ports:

```
         [ HTTP adapter ]
              ↓ port
[ UI adapter ] → Domain ← [ Storage adapter ]
```

Same as Clean Architecture; emphasizes **replaceable adapters** (swap payment provider, swap UI framework).

## Field normalization cookbook

Apply in **mapper only** — views assume fields exist and are display-ready.

| Rule | Example |
|------|---------|
| Date `YYYYMMDD` number → string | `20250828` → `"2025-08-28"` |
| Dictionary code → label | `{ status: 1 }` → `{ statusLabel: '进行中' }` |
| List wrapper key | API `{ items: [] }` → domain `{ records: [] }` |
| Status default | missing → `state: -1` |
| Null safety | `name ?? ''`, `tags ?? []` |
| Same semantic, same name | Always `productCode`, never mix `product_code` / `code` |
| Precision fields | `unitNv` rounded; add `unitNvOriginal` if sources differ |
| Unique list key | Ensure `id` on each record for `v-for` / `key` |
| No template filters | Ban `\| currency` chains — precompute in adapter |

## Result Pattern (error handling)

Standard return at service/repository/adapter boundary:

```typescript
interface DomainError {
  errorMsg: string
  errorCode: number | string
}

type Result<T> = [DomainError | null, T | null]
```

- HTTP success + business error → populate `error` field, still return typed entity default
- UI layer: `const [err, data] = await adapter()` — never unguarded `try/catch` in every component
- Use `await-to-js` or small `result()` helper consistently

## CQRS on frontend (lightweight)

Separate **commands** (mutations) from **queries** (reads) at application layer:

```
application/
├── commands/submit-training-job.command.ts
└── queries/load-project-list.query.ts
```

Benefits: clearer testing, different cache invalidation strategies. Full event sourcing is rarely justified on frontend.

## Micro-frontend boundaries

1. Each MFE owns local models — never share DTO types across bundles
2. Shell pub/sub messages mapped through ACL at subscriber
3. Minimize cross-MFE chatter; prefer backend as integration point
4. Enforce module boundaries (Nx tags, ESLint)

## Team ownership (suggested)

| Layer | Owner |
|-------|-------|
| Domain model & UL | Domain expert + senior FE |
| Application use cases | Senior FE |
| Infrastructure / ACL | Full-stack or platform FE |
| Presentation | Feature team |

Code review must check **dependency direction** before style nits.

## Metrics (refactoring success)

| Metric | Direction |
|--------|-----------|
| Components importing raw `api/` | ↓ |
| Duplicate business logic instances | ↓ |
| Domain unit test coverage | ↑ (target 80%+ on domain) |
| Files changed per API field rename | ↓ (ideally 1 mapper) |
| E2E count vs aggregate tests | E2E stable, aggregate tests ↑ |

## When NOT to use full DDD

- Prototype / MVP with <5 screens
- Marketing static site
- Pure presentation wrapper over stable SDK
- Team <2 with no planned longevity

Use instead: thin `api/` layer + composables + typed responses.

## Related patterns (not DDD but complementary)

| Pattern | Relation |
|---------|----------|
| **Strangler Fig** | Incremental migration to DDD layers |
| **Feature folders** | Vertical slice aligns with BC |
| **Smart/Dumb components** | Maps to feature/ui libraries |
| **Repository** | DDD tactical pattern — keep |
| **Active Record** | Avoid — anemic + fat ORM-style models |

## Glossary (中英)

| English | 中文 | Frontend note |
|---------|------|---------------|
| Bounded Context | 限界上下文 | Feature module boundary |
| Ubiquitous Language | 通用语言 | Shared naming in code |
| Entity | 实体 | Has identity |
| Value Object | 值对象 | Immutable, no identity |
| Aggregate Root | 聚合根 | Consistency entry point |
| Anti-Corruption Layer | 防腐层 | Mapper at API boundary |
| Repository | 仓储 | Data access abstraction |
| Domain Service | 领域服务 | Cross-entity logic |
| Application Service | 应用服务 | Use case orchestration |
