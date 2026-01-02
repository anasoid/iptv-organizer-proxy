# PRD: IPTV Organizer Proxy - Java/Quarkus for OpenWrt

## Project Overview

Build new Java/Quarkus implementation of IPTV Organizer Proxy with GraalVM native compilation for OpenWrt deployment with 64MB memory constraint.

**Deployment Model**: Standalone Quarkus native application
**Target State**: Independent Java service with 64MB heap running on OpenWrt via Docker
**No Migration**: Fresh implementation, old PHP application remains unchanged

---

## Scope & Deliverables (8 Phases)

### Phase 1: Project Setup & Infrastructure
**Goal**: Establish Quarkus project structure and build configuration
- Create Quarkus Gradle project with GraalVM native compilation
- Setup Gradle with Quarkus BOM 3.6.4+ and dependencies
- Configure project structure: controllers, services, models, migrations
- Setup version control and CI/CD pipeline

### Phase 2: Database Layer
**Goal**: Build database schema and ORM layer with custom migration system
- Implement custom SimpleMigrator (saves 3-4MB vs Flyway)
- Design and implement 11 MySQL schema files (adapting from PHP structure)
- Create Panache reactive entities for all tables
- Configure connection pooling (2 connections max)
- Test MySQL and SQLite support

### Phase 3: Core Streaming Components
**Goal**: Implement memory-efficient streaming and JSON parsing
- **StreamingJsonParser**: Jackson Streaming API (128KB chunks, GC every 1000 items)
- **HttpStreamingService**: Vert.x WebClient with zero-copy streaming
- **FilterService**: SnakeYAML parsing with regex-based pattern matching
- **SyncService**: Quarkus Scheduler with batch processing (100 items/batch)
- **LabelExtractor**: Implement label extraction from stream metadata

### Phase 4: Admin REST API & UI Integration
**Goal**: Build complete Admin REST API for React UI management
- **Auth Endpoints**: Login/logout, JWT token management
- **Sources CRUD**: Create, read, update, delete upstream IPTV sources
- **Clients CRUD**: Manage client credentials, expiry dates, access control
- **Filters CRUD**: Manage YAML filter configurations per source
- **Categories API**: Browse live/VOD/series categories
- **Streams API**: Browse live streams, VOD, series content
- **Sync Management API**: View sync status, logs, trigger manual sync
- **Connection Logs API**: View client connection history and activity
- **Dashboard API**: Statistics, source status, sync info
- **UI Testing**: Validate all endpoints with React admin UI

### Phase 5: Xtream Codes API & Proxy System
**Goal**: Implement full Xtream Codes API with dual proxy architecture
- **Xtream API** (`/player_api.php`): All client endpoints and actions
- **Stream Proxy Endpoints**: `/live/{user}/{pass}/{id}.{ext}`, `/movie/...`, `/series/...`
- **Client-Facing Proxy**: Application acts as proxy between IPTV clients and upstream servers
  - Handle 302 redirects from upstream servers
  - Base64-encoded proxy endpoint `/proxy/{user}/{pass}?url={encoded_url}`
  - Per-source configuration: `disablestreamproxy`, `stream_follow_location`
- **Upstream Proxy Support**: Route requests to upstream via HTTP/HTTPS/SOCKS5 proxy
  - Global and per-source proxy configuration
  - Support authentication (username/password)
  - ProxyConfigService for centralized configuration
- **Client Authentication**: Username/password validation per request
- **EPG Support**: Electronic Program Guide data
- **Test**: VLC, Kodi, TiviMate, GSE IPTV, other Xtream-compatible players

### Phase 6: Configuration & Deployment
**Goal**: Configure for OpenWrt deployment
- Convert `.env` to `application.yaml`
- Setup database connection properties
- Configure memory limits and GC settings
- Create Docker build configuration (multi-stage)
- Setup docker-compose for OpenWrt

### Phase 7: Testing & Optimization
**Goal**: Validate memory usage and performance
- Memory profiling: idle <45MB, load <60MB
- Load testing: 50 concurrent streams, 4 hours
- Functional testing: all endpoints
- OpenWrt hardware validation
- Performance benchmarking and tuning

### Phase 8: Documentation & Release
**Goal**: Finalize deliverables
- Deployment instructions
- Configuration reference
- Memory tuning guide
- API documentation (Xtream + Admin REST)
- Release notes

---

## Technical Requirements

### Why Reactive Stack is Critical for 64MB

**Problem**: Blocking I/O requires thread pools (each thread = 1-2MB memory)
- Traditional Java: 50 concurrent streams = 50 threads × 2MB = 100MB+ memory
- **64MB constraint**: Cannot support concurrent streams with blocking I/O

**Solution**: Reactive (Mutiny/Vert.x) uses event loops, not threads
- Single event loop handles 1000+ concurrent connections
- One thread per core (typical: 1-4 cores on OpenWrt)
- Memory: ~8MB for event loop + buffer, regardless of concurrent streams

**Result**: 50 concurrent streams = 2 threads × 4MB = 8MB overhead
**Savings**: 92MB less memory than blocking approach

### Technology Stack
- **Build Tool**: Gradle 8.5+
- **Framework**: Quarkus 3.6+ with GraalVM native image
- **Runtime**: Java 17+
- **Architecture**: Reactive (Mutiny/Vert.x) - **required for 64MB constraint**
- **GC**: Serial GC (minimal footprint)

### Memory Budget (64MB Heap)
```
Target breakdown:
- Idle baseline: ~24MB
- Streaming buffers (3 concurrent): ~12MB
- Database pools: ~8MB
- GC overhead: ~16MB
- Safety margin: ~4MB
Total: ~64MB
```

### Dependencies
- RESTEasy Reactive (REST framework)
- Hibernate Reactive Panache (ORM)
- Vert.x WebClient (HTTP client)
- Jackson Streaming API (JSON parsing)
- SnakeYAML Engine (YAML parsing)
- SmallRye JWT (authentication)
- Custom SimpleMigrator (database migrations)

---

## Functional Components to Implement

| Component | Reference (PHP) | Java Implementation | Details |
|-----------|-----------------|---------------------|---------|
| Video Streaming | HttpClient.php (547) | Vert.x WebClient, Multi<Buffer> | Zero-copy, 8-16KB chunks |
| JSON Parser | StreamingJsonParser.php (262) | Jackson Streaming API | 128KB chunks, GC every 1000 items |
| Sync Service | SyncService.php (923) | Quarkus Scheduler | Batch 100 items, explicit GC |
| Filter Engine | FilterService.php (789) | SnakeYAML + regex | Pattern matching, caching |
| Stream Proxy | StreamDataController.php (372) | Reactive REST endpoint | Route video streams |
| Client API | XtreamController.php | Implement /player_api.php endpoints | Full Xtream compatibility |
| Admin API | Admin/* (11 controllers) | RESTEasy Reactive | CRUD for all entities |
| Data Models | Models/* (11 tables) | Panache entities | Reactive ORM |
| Database | migrations/mysql/*.sql | Custom SimpleMigrator | Version tracking, checksums |

---

## Proxy Architecture

### Dual Proxy System

The application implements **two levels of proxying**:

#### 1. Client-Facing Proxy (Application ↔ IPTV Clients)
The application acts as an intermediary between IPTV clients (VLC, Kodi) and upstream IPTV servers.

**How it works:**
1. Client requests: `/live/{username}/{password}/{stream_id}.ts`
2. StreamDataController builds upstream URL and checks for 302 redirects
3. **Case A - No redirect**: Stream directly from upstream to client (pass-through)
4. **Case B - 302 redirect detected**:
   - If `disablestreamproxy=true`: Return 302 redirect directly to client (client connects to final URL)
   - If `disablestreamproxy=false` (default): Encode redirect URL in base64 and return 302 to `/proxy/{user}/{pass}?url={base64}`
5. ProxyController decodes URL, handles further redirects, and streams final data

**Benefit**: Handles complex redirect chains transparently to clients

#### 2. Upstream Proxy (Application ↔ Upstream Servers)
Route upstream API and stream requests through an optional HTTP/HTTPS/SOCKS5 proxy.

**Configuration:**
- **Global settings**: Environment variables (`PROXY_ENABLED`, `PROXY_URL`, `PROXY_TYPE`, `PROXY_HOST`, `PROXY_PORT`, `PROXY_USERNAME`, `PROXY_PASSWORD`)
- **Per-source override**: Source model field `enableproxy` (true/false)
  - `null/true`: Use global proxy if enabled
  - `false`: Bypass upstream proxy for this source

**Behavior:**
- ProxyConfigService loads configuration at startup
- ProxyConfigService.getCurlOptions() provides cURL configuration
- HttpClient applies proxy settings to all upstream requests
- Supports authentication (username/password)

#### 3. Source Configuration Flags
Each upstream source has three proxy-related settings:

| Flag | Purpose | Values |
|------|---------|--------|
| `enableproxy` | Use upstream proxy for this source | true/false/null |
| `disablestreamproxy` | Skip client-facing proxy for redirects | true/false |
| `stream_follow_location` | Follow HTTP redirects in streams | true/false |

---

## Admin REST API Specification

### Authentication
- **POST /api/auth/login** - Login with username/password, returns JWT token
- **POST /api/auth/logout** - Logout (invalidate token)
- All other endpoints require JWT token in Authorization header

### Sources Management (with Proxy Configuration)
- **GET /api/sources** - List all upstream IPTV sources
- **GET /api/sources/{id}** - Get source details
- **POST /api/sources** - Create new source
  - Fields: `name`, `url`, `username`, `password`, `sync_interval`
  - Proxy fields: `enableproxy` (null/true/false), `disablestreamproxy`, `stream_follow_location`
- **PUT /api/sources/{id}** - Update source (including proxy settings)
- **DELETE /api/sources/{id}** - Delete source
- **GET /api/sources/{id}/sync-status** - Get sync status for source
- **POST /api/sources/{id}/test-connection** - Test upstream connection

### Clients Management
- **GET /api/clients** - List all IPTV clients
- **GET /api/clients/{id}** - Get client details
- **POST /api/clients** - Create new client
- **PUT /api/clients/{id}** - Update client
- **DELETE /api/clients/{id}** - Delete client
- **GET /api/clients/{id}/history** - View client connection history

### Filters Management
- **GET /api/filters** - List all filters
- **GET /api/filters/{id}** - Get filter details
- **POST /api/filters** - Create new filter
- **PUT /api/filters/{id}** - Update filter
- **DELETE /api/filters/{id}** - Delete filter
- **POST /api/filters/{id}/validate** - Validate filter YAML syntax

### Content Discovery
- **GET /api/categories** - List categories by type (live/vod/series)
- **GET /api/categories/{id}/streams** - Get streams in category
- **GET /api/streams** - List all streams (paginated)
- **GET /api/streams/{id}** - Get stream details

### Synchronization
- **GET /api/sync/logs** - View sync operation logs
- **GET /api/sync/logs/{id}** - Get sync log details
- **POST /api/sync/trigger** - Trigger manual sync for source
- **GET /api/sync/status** - Get current sync status

### Monitoring
- **GET /api/connection-logs** - View client connection activity
- **GET /api/connection-logs?client={id}** - Filter by client
- **GET /api/dashboard** - Dashboard statistics and overview
  - Total sources, clients, streams
  - Last sync time, next sync time
  - Current active connections
  - System memory usage

---

## Xtream Codes API Endpoints (IPTV Client-Facing)

### Main Xtream API
- **GET /player_api.php** - Server info and authentication
- **GET /player_api.php?action=get_live_categories** - List live TV categories
- **GET /player_api.php?action=get_vod_categories** - List VOD categories
- **GET /player_api.php?action=get_series_categories** - List series categories
- **GET /player_api.php?action=get_live_streams** - List live streams with pagination
- **GET /player_api.php?action=get_vod_streams** - List VOD content
- **GET /player_api.php?action=get_series** - List series
- **GET /live/xmltv.php** - EPG data (XMLTV format)

### Stream Proxy Endpoints (with Dual Proxy Support)
- **GET /live/{username}/{password}/{stream_id}.ts** - Live TV stream
- **GET /movie/{username}/{password}/{stream_id}.mp4** - VOD stream
- **GET /series/{username}/{password}/{stream_id}.mp4** - Series stream

**Proxy behavior per source configuration:**
1. **Direct streaming** (no redirect): Proxy data directly from upstream
2. **With 302 redirect and disablestreamproxy=false**:
   - Application receives redirect from upstream
   - Returns 302 to `/proxy/{username}/{password}?url={base64_encoded_redirect_url}`
   - Client receives redirect and follows to proxy endpoint
   - ProxyController streams final data
3. **With 302 redirect and disablestreamproxy=true**:
   - Application returns 302 directly to client with upstream URL
   - Client connects directly to upstream (bypasses proxy)

### Internal Proxy Endpoint (Redirect Handling)
- **GET /proxy/{username}/{password}?url={base64_encoded_url}** - Internal redirect handler
  - Decodes base64 URL
  - Follows any additional redirects
  - Streams final data to client
  - Subject to upstream proxy if enabled

---

## Success Metrics

### Memory
- [ ] Idle state: < 45MB RSS
- [ ] Load (3 concurrent streams): < 60MB RSS
- [ ] Background sync: < 58MB RSS
- [ ] No OOM errors under sustained load

### Performance
- [ ] Startup time: < 2 seconds
- [ ] Video stream latency (first byte): < 200ms
- [ ] JSON parsing (10K items): < 3 seconds
- [ ] API response time (p99): < 100ms
- [ ] Throughput matches/exceeds PHP version

### Functionality
- [ ] All Xtream API endpoints working
- [ ] YAML filtering system operational
- [ ] Background sync daemon functional
- [ ] Admin REST API complete
- [ ] IPTV client compatibility (VLC, Kodi, TiviMate)

### Deployment
- [ ] Docker image < 80MB
- [ ] Successful build for ARM (armv7/armv8)
- [ ] Runs on OpenWrt with Docker
- [ ] Health check endpoint responsive
- [ ] React admin panel compatible

---

## Key Decisions

### 1. Custom SimpleMigrator vs Flyway
**Decision**: Implement custom migration system
**Rationale**: Saves 3-4MB memory (Flyway uses 4-6MB at runtime)
**Trade-off**: No rollback support (acceptable for embedded deployment)

### 2. Gradle vs Maven
**Decision**: Use Gradle 8.5+
**Rationale**: Faster builds, better caching, simpler native image configuration
**Implementation**: Configure via `quarkus.native` block in `build.gradle`

### 3. Reactive Architecture
**Decision**: Mutiny/Vert.x reactive stack
**Rationale**: Non-blocking I/O for video streaming, lower thread overhead
**Benefit**: Better concurrency with same memory footprint

### 4. Connection Pooling
**Decision**: Minimal pools (2 DB, 3 HTTP)
**Rationale**: Fits within 64MB memory constraint
**Impact**: Sequential connection reuse, acceptable latency

---

## Risks & Mitigation

| Risk | Impact | Likelihood | Mitigation |
|------|--------|------------|-----------|
| Memory overflow during sync | Critical | Medium | Batch tuning (100 items), explicit GC calls |
| GraalVM reflection issues | High | Medium | Comprehensive reflection config, early testing |
| Native image size > 80MB | High | Low | Dependency optimization, tree-shaking |
| Stream buffer exhaustion | High | Low | Connection limits, backpressure handling |
| Database connection leaks | Medium | Low | Pool monitoring, timeout enforcement |
| YAML filter incompatibility | Medium | Medium | Unit tests with existing PHP configs |
| OpenWrt hardware compatibility | High | Medium | Test on real device early, ARM cross-compile |

---

## Timeline & Milestones

### Milestone 1: Infrastructure & Database (Weeks 1-2)
- Quarkus project setup with Gradle
- Database layer with custom SimpleMigrator
- Connection pooling configured
- Panache entities for all 11 tables
- **Deliverable**: Working database, migration system, ORM models

### Milestone 2: Core Services (Weeks 2-4)
- Streaming components (JSON parser, HTTP client, filters)
- Background sync daemon with Quarkus Scheduler
- Label extraction service
- All services tested for memory efficiency
- **Deliverable**: <60MB memory under load

### Milestone 3: Admin REST API for UI (Week 4-5)
- Complete Admin REST API with 9 controllers
- Auth, sources, clients, filters, categories, streams, sync, logs, dashboard
- JWT authentication
- Full integration with React admin UI
- **Deliverable**: Fully functional admin panel backend

### Milestone 4: Xtream Codes API (Week 5)
- Xtream API implementation (/player_api.php endpoints)
- Stream proxy with video streaming
- EPG support
- Full client compatibility testing
- **Deliverable**: IPTV client integration (VLC, Kodi, etc.)

### Milestone 5: Deployment & Optimization (Weeks 6-7)
- Docker configuration with multi-stage builds
- Native image compilation for OpenWrt
- Configuration management (application.yaml)
- Memory profiling and optimization
- OpenWrt hardware validation
- **Deliverable**: Production-ready <80MB Docker image, <2s startup

---

## Implementation Steps

### Phase 1: Project Setup & Infrastructure
- [ ] Create Quarkus Gradle project (build.gradle)
- [ ] Add Quarkus platform BOM 3.6.4+
- [ ] Add reactive extensions: resteasy-reactive, hibernate-reactive-panache, reactive-mysql-client
- [ ] Add dependencies: Vert.x WebClient, Jackson, SnakeYAML, SmallRye JWT
- [ ] Configure native build: -Xmx64m, --gc=serial, -march=armv7-a
- [ ] Setup package structure: controllers, services, models, migrations

### Phase 2: Database Layer
- [ ] Create custom migration system (SimpleMigrator pattern)
- [ ] Design and implement 11 SQL schema files in /resources/db/migration/
- [ ] Implement version tracking table (schema_version)
- [ ] Add checksum validation
- [ ] Create Panache entities for 11 tables: AdminUser, Source, Filter, Client, Category, LiveStream, VodStream, Series, SyncLog, ConnectionLog
- [ ] Configure reactive pool: max-size=2, idle-timeout=30s
- [ ] Test MySQL and SQLite support

### Phase 3: Core Services
- [ ] **StreamingJsonParser**: Jackson Streaming API, 128KB chunks, GC every 1000 items
- [ ] **HttpStreamingService**: Vert.x WebClient, Multi<Buffer>, 8-16KB chunks, backpressure
- [ ] **FilterService**: SnakeYAML parsing, regex matching, filter caching
- [ ] **SyncService**: Quarkus Scheduler, batch 100 items, explicit GC, transaction safety
- [ ] **LabelExtractor**: Implement label extraction from stream metadata

### Phase 4: Admin REST API & UI Integration
- [ ] **AuthController**: POST /api/auth/login, POST /api/auth/logout, JWT token generation
- [ ] **SourceController**: GET/POST/PUT/DELETE /api/sources, list and manage upstream sources
- [ ] **ClientController**: GET/POST/PUT/DELETE /api/clients, manage IPTV clients and credentials
- [ ] **FilterController**: GET/POST/PUT/DELETE /api/filters, manage YAML filter configurations
- [ ] **CategoriesController**: GET /api/categories, browse available categories
- [ ] **StreamsController**: GET /api/streams, browse live streams, VOD, series content
- [ ] **SyncController**: GET /api/sync/logs, POST /api/sync/trigger, manage background sync
- [ ] **ConnectionLogsController**: GET /api/connection-logs, view client activity
- [ ] **DashboardController**: GET /api/dashboard, statistics and overview data
- [ ] Test all endpoints with React admin UI

### Phase 5: Xtream Codes API & Proxy System
- [ ] **XtreamController**: GET /player_api.php with all actions (get_live_categories, get_live_streams, etc.)
- [ ] **StreamDataController**:
  - Implement `/live/{user}/{pass}/{id}.{ext}`, `/movie/...`, `/series/...` endpoints
  - Handle upstream 302 redirects (redirect directly or via proxy)
  - Support per-source configuration: `disablestreamproxy`, `stream_follow_location`
  - Direct streaming when no redirect, proxy when redirect detected
- [ ] **ProxyController**:
  - Implement `/proxy/{user}/{pass}?url={base64_encoded}` endpoint
  - Decode base64 URLs and stream data
  - Support redirect following based on source configuration
- [ ] **ProxyConfigService**:
  - Manage upstream proxy configuration (HTTP/HTTPS/SOCKS5)
  - Support global (PROXY_ENABLED, PROXY_URL) and per-source proxy settings
  - Handle proxy authentication (username/password)
  - Provide configuration for cURL requests
- [ ] **ClientAuthMiddleware**: Validate client credentials per request
- [ ] **EPG Support**: GET /live/xmltv.php or similar EPG endpoints
- [ ] **Response Formatting**: JSON compliance with Xtream API specification
- [ ] Test with VLC, Kodi, TiviMate, GSE IPTV, other compatible players
- [ ] Test proxy scenarios: direct, 302 redirects, upstream proxy, SOCKS5

### Phase 6: Configuration & Deployment
- [ ] Convert .env to application.yaml
- [ ] Database connection properties
- [ ] Sync scheduler intervals (DEFAULT_SYNC_INTERVAL, SYNC_CHECK_INTERVAL)
- [ ] Memory and pool limits
- [ ] JWT secret configuration
- [ ] CORS and security settings

### Phase 7: Docker & Testing
- [ ] Multi-stage Dockerfile: Gradle builder + Alpine runtime
- [ ] Native image build configuration
- [ ] ARM architecture optimization (armv7/armv8)
- [ ] Image size optimization (target: <80MB)
- [ ] Health check endpoint (/health)
- [ ] docker-compose.yml for OpenWrt

### Phase 8: Validation & Optimization
- [ ] Memory profiling: idle <45MB, load <60MB, sync <58MB
- [ ] Load testing: 50 concurrent streams, 4 hours
- [ ] Functional testing: all Admin API endpoints with React UI
- [ ] Functional testing: all Xtream API endpoints with IPTV clients
- [ ] OpenWrt hardware deployment validation
- [ ] Startup time <2s verification

---

## Dependencies & Resources

### External Systems
- MySQL 8.0+ or MariaDB 10.5+
- OpenWrt 22.03+ with Docker support
- GraalVM CE 22.3+ for native compilation

### Build Infrastructure
- Gradle 8.5+
- Java 17+
- Docker for multi-stage builds
- CI/CD pipeline (GitHub Actions)

### Testing Infrastructure
- JFR (Java Flight Recorder) for memory profiling
- JMeter or custom load testing
- IPTV clients (VLC, Kodi, TiviMate)
- OpenWrt device or emulator

---

## Acceptance Criteria

### Code Quality
- [ ] Memory usage validated on target hardware
- [ ] All unit tests passing
- [ ] Integration tests for critical paths
- [ ] Code follows Java conventions
- [ ] No compiler warnings

### Performance
- [ ] Startup time < 2s on target device
- [ ] Video streaming latency < 200ms
- [ ] API response times < 100ms (p99)
- [ ] Handles 50 concurrent streams

### Admin API Endpoints
- [ ] POST /api/auth/login - User authentication
- [ ] POST /api/auth/logout - User logout
- [ ] GET /api/sources - List upstream sources
- [ ] POST/PUT/DELETE /api/sources - CRUD sources
- [ ] GET /api/clients - List IPTV clients
- [ ] POST/PUT/DELETE /api/clients - CRUD clients
- [ ] GET /api/filters - List filters
- [ ] POST/PUT/DELETE /api/filters - CRUD filters
- [ ] GET /api/categories - Browse categories
- [ ] GET /api/streams - Browse streams
- [ ] GET /api/sync/logs - View sync logs
- [ ] POST /api/sync/trigger - Manual sync trigger
- [ ] GET /api/connection-logs - View client activity
- [ ] GET /api/dashboard - Dashboard statistics

### Xtream API Compatibility
- [ ] Xtream API 100% compatible (/player_api.php)
- [ ] Works with VLC, Kodi, TiviMate, GSE IPTV
- [ ] Stream proxy functional (/live/..., /movie/..., /series/...)
- [ ] EPG support (XMLTV format)
- [ ] MySQL and SQLite databases work
- [ ] Runs on OpenWrt ARMv7/ARMv8

### Operations
- [ ] Docker image < 80MB
- [ ] Health check endpoint
- [ ] Startup < 2 seconds
- [ ] No memory leaks (4-hour test)
- [ ] Graceful shutdown
- [ ] Configuration via environment variables

---

## Critical Implementation Notes

### Proxy System (Phase 5 Priority)
- **Dual proxy architecture** is core to application functionality
- **Client-facing proxy** handles redirect chains transparently (base64 encoding/decoding)
- **Upstream proxy** allows routing through HTTP/HTTPS/SOCKS5 with authentication
- Per-source proxy configuration is essential for flexibility
- ProxyConfigService must support both unified URL and component-based environment variables

### Database Schema
All 11 tables include these fields:
- Timestamps: `created_at`, `updated_at`
- Primary key: `id` (auto-increment)
- Source table includes: `enableproxy`, `disablestreamproxy`, `stream_follow_location`
- Client table linked to Source via `source_id`
- Filter table linked to Source via `source_id`

### General Notes
- **No Migration Required**: Old PHP application remains separate; this is new Java implementation
- React admin panel can remain unchanged (separate TypeScript/React service)
- OpenWrt deployment uses Docker (standalone container)
- Custom migration system chosen for 3-4MB memory savings vs Flyway
- Gradle chosen for build efficiency and native image configuration
- Reactive architecture critical for 64MB heap constraint
- Fresh implementation focused on memory efficiency and OpenWrt compatibility

---

**Document Version**: 1.1 (Updated with Proxy Architecture)
**Created**: 2026-01-02
**Last Updated**: 2026-01-02
**Status**: Approved for New Development
