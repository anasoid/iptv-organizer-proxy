# AGENTS.md — IPTV Organizer Proxy

## Project Layout

```
/back      → Active Java/Quarkus backend (the live project — ignore the README which describes the deprecated PHP era)
/admin     → React + Vite admin panel (TypeScript, MUI, TanStack Query, Zustand)
/java      → Legacy/empty directory (bin/ only — do not use)
/docker    → Docker / Docker Compose files for production
/docs      → Architecture & implementation notes
```

> `CLAUDE.md` mislabels `/back` as "deprecated PHP". It is the **active** Quarkus Java backend.

---

## Backend Architecture (`back/`)

**Stack:** Quarkus 3.31.1 · Java 21 · Gradle · SQLite (prod/Docker) / MySQL (`%dev`) / H2 (tests)

**Package root:** `org.anasoid.iptvorganizer`

### Layer structure

| Layer | Path | Notes |
|---|---|---|
| Controllers | `controllers/admin/` + `controllers/xtream/` + `controllers/proxy/` | Admin REST uses JWT (`@RolesAllowed`). Xtream endpoints use Xtream username/password — **no JWT**. |
| Services | `services/` | All `@ApplicationScoped`. CRUD services extend `BaseService<T, R>`. |
| Repositories | `repositories/` | Raw JDBC, no ORM. All extend `BaseRepository<T>` which provides cache + JDBC helpers. |
| Migrations | `migrations/SimpleMigrator.java` | Custom SQL migrator (no Flyway/Liquibase). SQL files in `resources/db/migration/{sqlite,mysql,h2}/`. **Migration list is hardcoded** — add new file names to `SimpleMigrator.MIGRATIONS`. |
| Cache | `cache/CacheManager.java` + `cache/Cache.java` | In-process, TTL-based, named caches. Repositories inject `CacheManager` and call `cacheManager.getCache(name, maxSize)`. |

### Stream-mode pipeline

Every Xtream stream request resolves a `ConnectXtreamStreamMode` (REDIRECT / DIRECT / NO_PROXY / PROXY) and is dispatched by `utils/streaming/StreamModeHandler.java`:
- **REDIRECT** → 302 to upstream URL
- **DIRECT** → HEAD check for upstream redirect (hides upstream credentials from client)
- **NO_PROXY / PROXY** → server-side proxy via `HttpStreamingService`

Controllers for streaming extend `AbstractDataController` which provides the common `getStream(...)` logic. `TimeshiftController` overrides it to always redirect.

### Data-flow: Xtream API request
```
XtreamController / StreamDataController / TimeshiftController
  → XtreamUserService.authenticateAndValidateClient()   (Client + Source lookup)
  → ContentFilterService.shouldIncludeStream()          (filter + adult-content check)
  → ClientService.resolveConnectXtreamStream()          (resolve DEFAULT mode)
  → StreamModeHandler                                   (REDIRECT / DIRECT / PROXY / NO_PROXY)
```

### Sync pipeline
`SyncManager` (Quarkus `@Scheduled`) → `LiveSynchronizer` / `VodSynchronizer` / `SeriesSynchronizer` → `XtreamClient` (upstream API) → repositories. Migration must finish before sync starts (`SimpleMigrator.isMigrationDone()`).

### Key entities
`Source` (upstream Xtream server) → `Client` (proxied end-user) → `Filter` (YAML rules) → `Proxy` (HTTP proxy config) · `LiveStream / VodStream / Series / Category` (synced content)

### Filter system
`FilterService` parses YAML stored in `Filter.content` using Jackson YAML into `FilterConfig` → `FilterRule` objects. Rules match on `FilterField` (name, category, isAdult, …) with allow/deny `FilterAction`.

---

## Developer Commands

### Backend (run from `back/`)
```bash
./gradlew quarkusDev          # Dev mode with live reload; uses MySQL (see %dev in application.properties)
./gradlew test                # Tests use H2 in-memory (H2TestProfile), scheduler disabled
./gradlew spotlessApply       # Auto-format with google-java-format
./gradlew spotlessCheck       # Lint check
./gradlew codeQuality         # PMD + Spotless + JaCoCo
./gradlew quarkusBuild        # Production fast-jar (SQLite default)
```

### Admin frontend (run from `admin/`)
```bash
npm run dev                   # Dev server, proxies /api → localhost:8080
npm run build:java            # Build + copy dist → back/src/main/resources/META-INF/resources/admin/
npm run build                 # Standalone build
```

### Docker
```bash
docker-compose -f docker/docker-compose.yml up --build
# Exposes :9090 → container :8080, SQLite persisted in Docker volume
```

---

## Configuration

- **Production/Docker:** `application.properties` — SQLite, `app.datasource.dialect=sqlite`
- **Dev profile:** `%dev.*` overrides in `application.properties` — MySQL on localhost:3306
- **Tests:** `H2TestProfile` provides H2 in-memory overrides via `QuarkusTestProfile`
- **Admin panel:** `VITE_API_BASE_URL` and `VITE_BASE_PATH` env vars (injected as `__API_BASE_URL__` / `__BASE_PATH__` at build time)

---

## Admin Frontend Architecture (`admin/`)

**Stack:** React 19 · TypeScript · MUI v7 · TanStack React Query v5 · Zustand · Axios · React Hook Form · Monaco Editor (YAML filter editor)

- Auth state: `stores/authStore.ts` (Zustand) — JWT stored in `localStorage` as `auth_token`
- API calls: `services/api.ts` (Axios instance) with request interceptor (attaches token) and response interceptor (redirects to login on 401)
- Domain APIs: one file per resource in `services/` (e.g., `sourcesApi.ts`, `clientsApi.ts`)
- Pages in `pages/`, shared components in `components/`

---

## Adding a New Entity — Checklist

1. **Entity** — add `models/entity/MyEntity.java` extending `BaseEntity`
2. **Migration** — add `V0XX__create_my_entity.sql` in all three dialect folders (`sqlite/`, `mysql/`, `h2/`); add filename to `SimpleMigrator.MIGRATIONS`
3. **Repository** — extend `BaseRepository<MyEntity>`, override `getTableName()` and `mapRow()`
4. **Service** — extend `BaseService<MyEntity, MyEntityRepository>`
5. **Admin controller** — extend `BaseController`, annotate with `@RolesAllowed("admin")`
6. **Frontend** — add `services/myEntityApi.ts`; add page under `pages/`; wire route in `App.tsx`

---

## Project-Specific Conventions

- No JPA/Hibernate — all SQL is hand-written in repositories via `PreparedStatement`
- Boolean fields in SQLite are stored as integers; use `BooleanAsIntSerializer` for Jackson serialisation
- `@Slf4j` (Lombok) is the only logger; never use `java.util.logging` or `System.out`
- Admin API path prefix: `/api/*` · Xtream API paths: `/player_api.php`, `/live/{u}/{p}/…`, `/timeshift/…`, `/xmltv.php`
- Unit tests use plain JUnit 5 + Mockito (`@ExtendWith(MockitoExtension.class)`); integration tests use `@QuarkusTest` + `@TestProfile(H2TestProfile.class)`
- **Never commit directly** — the user reviews and commits all changes

