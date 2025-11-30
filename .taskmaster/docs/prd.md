# IPTV Organizer Proxy - Product Requirements Document

<context>
## Overview
The IPTV Organizer Proxy is a middleware service that synchronizes data from Xtream Codes API sources, stores it in a local database, and provides filtered access through the standard Xtream Codes API protocol. It enables personal users to organize, filter, and customize IPTV content for themselves and family members with granular access control. Each user/device can be assigned to a source with custom filters applied.

**Target Users:**
- Personal IPTV users managing their own content
- Self-hosters running personal IPTV proxy services
- Home users organizing IPTV for family members
- Individual users wanting to filter and customize their IPTV content

**Value Proposition:**
- Simple one-source-per-client architecture
- Granular content filtering per client
- Automatic daily synchronization with sources
- Standard Xtream Codes API compatibility with all clients
- Local caching reduces load on source servers
- Modern React-based administration interface
- Docker deployment for easy installation
- Configurable database (MySQL or SQLite)

## Core Features

### 1. Xtream Codes Source Synchronization
**What it does:** Connects to Xtream Codes servers, fetches all content data (live channels, VOD movies, series), and stores locally.

**Why it's important:** Foundation for the entire system; enables offline operation and fast response times.

**How it works:**
- Connect to Xtream Codes API using credentials (username, password, URL)
- **Synchronization is separated by categories and stream types:**
  - **Live Sync Tasks:**
    1. Sync live categories (`get_live_categories`)
    2. Sync live streams (`get_live_streams`)
  - **VOD Sync Tasks:**
    1. Sync VOD categories (`get_vod_categories`)
    2. Sync VOD streams (`get_vod_streams`)
  - **Series Sync Tasks:**
    1. Sync series categories (`get_series_categories`)
    2. Sync series (`get_series`)
- Each task is separate and can be tracked independently
- Store all metadata in local database (MySQL or SQLite)
- **Extract labels** from channel names and category names:
  - Split by `-` (dash) delimiter and trim whitespace
  - Split by `|` (pipe) delimiter and trim whitespace
  - Extract text between `[]` (square brackets) as separate labels
  - Remove `[]` brackets from the main text to get clean labels
  - Add `stream_type` as a label (e.g., "live", "movie", "series")
  - Example: "ESPN [HD] | Sports - USA" (live stream) → labels: "ESPN", "HD", "Sports", "USA", "live"
  - Example: "FR: TF1 [FHD]" (live stream) → labels: "FR", "TF1", "FHD", "live"
  - Example: "Movies - Action | English" (VOD) → labels: "Movies", "Action", "English", "movie"
  - Store labels in database for advanced filtering, grouping, and search
- Scheduled synchronization (daily, configurable interval)
- Track sync status and last update time per source
- Handle API errors with logging

### 2. Client Access via Xtream Codes API
**What it does:** Provides standard Xtream Codes API endpoints for clients to access filtered content from their assigned source.

**Why it's important:** Ensures compatibility with all existing Xtream Codes-compatible IPTV players (TiviMate, IPTV Smarters, etc.).

**How it works:**
- Implement Xtream Codes API endpoints:
  - `player_api.php?username=X&password=Y` (authentication)
  - `player_api.php?username=X&password=Y&action=get_live_categories`
  - `player_api.php?username=X&password=Y&action=get_live_streams` (all streams)
  - `player_api.php?username=X&password=Y&action=get_live_streams&category_id=X` (filtered by category)
  - `player_api.php?username=X&password=Y&action=get_vod_categories`
  - `player_api.php?username=X&password=Y&action=get_vod_streams` (all VOD)
  - `player_api.php?username=X&password=Y&action=get_vod_streams&category_id=X` (filtered by category)
  - `player_api.php?username=X&password=Y&action=get_series_categories`
  - `player_api.php?username=X&password=Y&action=get_series` (all series)
  - `player_api.php?username=X&password=Y&action=get_series&category_id=X` (filtered by category)
  - `player_api.php?username=X&password=Y&action=get_simple_data_table&stream_id=X` (EPG - proxied without filtering)
  - `player_api.php?username=X&password=Y&action=get_short_epg&stream_id=X&limit=X` (short EPG - proxied without filtering)
  - `xmltv.php?username=X&password=Y` (full XMLTV - proxied and filtered on-the-fly)
  - `xmltv.php?username=X&password=Y&stream_id=X` (single stream XMLTV - proxied without filtering)
  - Stream URLs: `/live/{username}/{password}/{stream_id}.{ext}` (any extension)
  - VOD URLs: `/movie/{username}/{password}/{stream_id}.{ext}` (any extension)
  - Series URLs: `/series/{username}/{password}/{stream_id}.{ext}` (any extension)
  - Extensions can be: m3u8, ts, mp4, mkv, avi, flv, or any other format from source
- **Stream URL Proxying:**
  - Client requests: `http://proxy-server/live/client_user/client_pass/12345.m3u8`
  - Proxy authenticates client and verifies access to stream
  - Proxy replaces domain and credentials with source: `http://source-server/live/source_user/source_pass/12345.m3u8`
  - Proxy redirects or reverse-proxies to the source URL
  - Same logic applies for /movie/ and /series/ URLs
- **Channels, categories, VOD, series**: Filtered from local SQL database cache
- **EPG data**: NOT stored in database - proxied on-the-fly from source
  - EPG with stream_id: proxy directly without filtering
  - Full XMLTV (no stream_id): proxy and filter on-the-fly to client's accessible streams
- Return filtered data in standard Xtream format

### 3. Filter Management System
**What it does:** Allows administrators to create and assign filters that control what content each client can access from their assigned source.

**Why it's important:** Core business logic for content distribution and access control.

**How it works:**
- **Filter Structure (YAML):**
  - Filter has two separate sections:
    1. **rules**: list of include/exclude rules
    2. **favoris**: list of favoris rules (separate field)

  - **Each include/exclude rule has:**
    - **name**: descriptive name for the rule
    - **type**: include or exclude
    - **match**: criteria for matching content
      - **categories**: match by category name or labels
      - **channels**: match by channel name or labels

  - **Each favoris rule has:**
    - **name**: descriptive name for the favoris
    - **target_group**: name of the favorite group/category (virtual category)
    - **match**: criteria for matching content
      - **categories**: match by category name or labels
      - **channels**: match by channel name or labels

- **Rule Types:**
  - **include**: whitelist matching categories/channels
  - **exclude**: blacklist matching categories/channels
  - **favoris** (separate field): add matching categories/channels to a favorite group (virtual category)

- **Matching Criteria:**
  - Match by **name**: exact or partial category/channel name matching
  - Match by **labels**: match extracted labels (HD, FHD, 4K, country codes, etc.)
  - Can match on categories, channels, or both

- **Favorite Groups (Virtual Categories):**
  - Create virtual categories from favoris section (separate from rules)
  - Channels/categories appear in their target group
  - Groups appear as categories in client API
  - **Favoris ID Generation:**
    - Favoris category IDs start from 100000
    - Order respects the order of favoris in the favoris section
    - First favoris → category_id: 100000
    - Second favoris → category_id: 100001
    - Third favoris → category_id: 100002, etc.
  - **Favoris Calculation by Content Type:**
    - Process separately for each type: live, movies, series
    - For `get_live_categories`: check all favoris, create virtual categories for live streams, **favoris appear first**
    - For `get_vod_categories`: check all favoris, create virtual categories for movies, **favoris appear first**
    - For `get_series_categories`: check all favoris, create virtual categories for series, **favoris appear first**
    - **Category Order:** Favoris categories (100000+) are listed first, then regular categories

- Include/exclude rules are applied in order within the rules section
- Favoris are processed separately from the favoris section
- Each client has ONE filter assigned
- Each filter contains YAML with two sections: rules (include/exclude) and favoris
- Preview filter results before applying
- Template filters for common configurations

### 4. React-based Administration Web Interface
**What it does:** Provides a modern, responsive web-based dashboard for managing sources, clients, and filters.

**Why it's important:** Essential for day-to-day operations and configuration with excellent user experience.

**How it works:**
- **Source Management:**
  - Add/edit/delete Xtream Codes sources
  - Configure source credentials (URL, username, password)
  - Set sync interval per source
  - View sync status and last update
  - Manual sync trigger
  - Test source connection

- **Client Management:**
  - Add/edit/delete clients
  - Generate client credentials (username/password)
  - Assign source to client (one source per client)
  - Assign filter to client (one filter per client, optional)
  - Set expiration dates
  - Enable/disable clients
  - View client connection logs

- **Filter Management:**
  - Create/edit/delete filters
  - Configure filters in YAML format with two sections:
    - **rules**: include/exclude rules
    - **favoris**: favoris rules (separate)
  - YAML editor with syntax highlighting
  - Preview filtered content
  - Clone filters
  - Import/export filter configurations (YAML files)

- **Dashboard:**
  - System overview (total sources, clients, streams)
  - Recent activity logs
  - Sync status for all sources
  - Quick actions

### 5. Automatic Data Refresh
**What it does:** Scheduled background jobs that synchronize data from sources at configurable intervals without requiring cron.

**Why it's important:** Keeps local database up-to-date with source changes without manual intervention.

**How it works (multiple options for servers without cron):**

**Option 1: PHP Daemon Worker (Recommended)**
- Long-running PHP process in Docker container
- Continuous loop with sleep intervals
- Checks each source's next_sync timestamp
- Automatically restarts on failure (Docker restart policy)
- No external dependencies

**Option 2: External Webhook Trigger**
- Expose HTTP endpoint for triggering sync (authenticated)
- Use free external services to call webhook:
  - cron-job.org (free, reliable)
  - EasyCron (free tier available)
  - GitHub Actions scheduled workflows
  - UptimeRobot (monitoring + cron alternative)

**Option 3: On-Demand Sync with TTL**
- Check data freshness on each API request
- Trigger async sync if data is stale (TTL expired)
- Background sync doesn't block client requests
- Lazy loading approach

**Sync Features (all options):**
- Configurable refresh interval per source (default: 24 hours)
- **Separate sync tasks by type:**
  - Live categories sync
  - Live streams sync
  - VOD categories sync
  - VOD streams sync
  - Series categories sync
  - Series sync
- Each task tracked independently in sync_logs
- Incremental updates when possible
- Full resync option per task type
- Conflict resolution (handle deleted/updated content)
- Notification on sync failures
- Sync logs and history per task type
- Lock mechanism to prevent duplicate syncs per task type

### 6. Docker Deployment
**What it does:** Provides containerized deployment with Docker for easy installation and portability.

**Why it's important:** Simplifies deployment, ensures consistency across environments, enables easy updates.

**How it works:**
- Multi-stage Docker build for optimized image size
- Separate containers for PHP backend and React frontend
- Docker Compose configuration for full stack
- Environment variable configuration
- Volume mounts for persistent data
- Health checks for containers
- Official Docker Hub image publishing

### 7. CI/CD with GitHub Actions
**What it does:** Automated build, test, and deployment pipeline using GitHub Actions.

**Why it's important:** Ensures code quality, automates releases, publishes Docker images automatically.

**How it works:**
- Automated testing on pull requests
- Docker image build on commits to main branch
- Multi-platform builds (amd64, arm64)
- Automatic versioning (semantic versioning)
- Docker image push to Docker Hub/GitHub Container Registry
- Release artifact generation
- Automated changelog generation

## User Experience

### User Personas

**Persona 1: The IPTV Reseller**
- Purchases access from wholesale provider
- Needs to filter content for different client packages
- Wants to hide adult content for family packages
- Sells different tiers (basic, premium, sports)
- Manages 100-1000 clients
- Each client gets filtered access to the same source

**Persona 2: The Service Administrator**
- Manages technical infrastructure
- Monitors system health and performance
- Troubleshoots client connection issues
- Performs maintenance and updates
- Values Docker for easy deployment
- Uses modern admin UI efficiently

**Persona 3: The Self-Hoster**
- Runs personal IPTV setup
- Wants SQLite for simplicity
- Uses Docker on home server
- Manages family accounts with different filters
- Values easy updates and maintenance

### Key User Flows

**Flow 1: Initial System Setup**
1. Administrator pulls Docker image or clones repository
2. Administrator configures environment (database choice, credentials)
3. Administrator runs Docker Compose
4. Administrator accesses React admin panel
5. Administrator adds first Xtream Codes source
6. System performs initial synchronization
7. Administrator creates basic filters (YAML with two sections: rules and favoris)
8. Administrator adds first client with source and one filter
9. Administrator provides credentials to end client

**Flow 2: Adding a New Source**
1. Administrator navigates to Sources page in React UI
2. Administrator clicks "Add Source" button
3. Administrator enters source details in form
4. Administrator tests connection (instant feedback)
5. Administrator sets sync interval
6. Administrator saves source
7. System performs initial sync (progress shown)
8. Administrator receives notification on completion

**Flow 3: Creating a Client with Filter**
1. Administrator navigates to Clients page
2. Administrator clicks "Add Client"
3. Administrator enters client name and details
4. Administrator selects source from dropdown (required)
5. System generates or administrator sets credentials
6. Administrator selects one filter to apply (optional)
7. Administrator sets expiration date (optional)
8. Administrator saves client
9. System displays client credentials and connection URL
10. Administrator copies credentials to clipboard

**Flow 4: Client Connecting to Service**
1. End user enters credentials in IPTV player
2. Player sends authentication request to proxy
3. Proxy validates credentials and identifies assigned source
4. Proxy returns user info and server details
5. Player requests channel list
6. Proxy applies filters and returns filtered list from client's source
7. User selects channel to watch
8. Proxy proxies stream URL to client's assigned source
9. User watches content seamlessly

**Flow 5: Daily Sync Operation**
1. Sync daemon or cron job triggers at scheduled time
2. **For each source, runs 6 separate sync tasks:**
   - Task 1: Sync live categories
   - Task 2: Sync live streams
   - Task 3: Sync VOD categories
   - Task 4: Sync VOD streams
   - Task 5: Sync series categories
   - Task 6: Sync series
3. Each task fetches updated data from source
4. Each task compares with local database
5. Each task updates changed content
6. Each task removes deleted content
7. Each task adds new content
8. System logs each task result separately in sync_logs with sync_type
9. React dashboard shows updated sync status per task type

### UI/UX Considerations
- Modern React SPA with Material-UI or Ant Design
- Responsive design (desktop, tablet, mobile)
- Real-time updates using WebSockets or polling
- Data tables with sorting, filtering, search, pagination
- Form validation with inline error messages
- Loading states and progress indicators
- Toast notifications for actions
- Confirmation modals for destructive actions
- Dark/light theme support
- Breadcrumb navigation
- Clean, intuitive layout
- Copy-to-clipboard functionality for credentials
</context>

<PRD>
## Technical Architecture

### System Components

**1. PHP Backend API**
- RESTful API server (using Slim Framework or similar)
- Xtream Codes API implementation
- Client authentication and authorization
- Source synchronization logic
- Filter application engine
- Stream URL proxying
- EPG proxy with on-the-fly filtering

**Data Architecture:**
- **Stored in database**: Channels, categories, VOD, series metadata
- **Proxied on-the-fly**: EPG data, stream URLs
- Filters applied to database queries for cached content
- Filters applied in real-time for proxied EPG data

**2. React Frontend (Admin Panel)**
- React 18+ with TypeScript
- State management (Redux Toolkit or Zustand)
- UI component library (Material-UI or Ant Design)
- React Router for navigation
- Axios for API calls
- Form handling (React Hook Form)
- Real-time updates (SWR or React Query)
- Build with Vite or Create React App

**3. Database (Configurable)**
- **MySQL 8.0+** (for production, multi-user deployments)
- **SQLite 3** (for development, single-user, embedded deployments)
- Single configuration option determines which to use
- Same schema supports both databases
- Migration scripts for both database types

**4. Background Sync Worker**
- PHP CLI script or daemon
- Cron-triggered or continuous loop
- Queue-based processing
- Handles all source synchronization
- Error handling and logging
- Logging to database and files

**5. Web Server (Nginx)**
- Serve React static files
- Reverse proxy to PHP backend
- Handle stream proxying
- URL rewriting for Xtream API format
- SSL/TLS termination
- Access logging

**6. Docker Infrastructure**
- **Backend Container:** PHP-FPM with Nginx
- **Frontend Container:** Nginx serving React build
- **Database Container:** MySQL (optional, if not using SQLite)
- **Sync Worker Container:** PHP CLI for background jobs
- Docker Compose orchestration
- Volume mounts for data persistence
- Network isolation and security

**7. CI/CD Pipeline (GitHub Actions)**
- Automated testing (PHPUnit, Jest)
- Code quality checks (PHPStan, ESLint)
- Docker image builds (multi-stage)
- Multi-platform support (linux/amd64, linux/arm64)
- Push to Docker Hub and GitHub Container Registry
- Semantic versioning and tagging
- Release notes generation

### Data Models

**sources**
```sql
- id (PRIMARY KEY)
- name (VARCHAR)
- url (VARCHAR) - base URL of Xtream server
- username (VARCHAR) - source credentials
- password (VARCHAR) - source credentials
- sync_interval (INT) - hours between syncs
- last_sync (DATETIME)
- next_sync (DATETIME)
- sync_status (ENUM: idle, syncing, error)
- is_active (BOOLEAN)
- created_at (DATETIME)
- updated_at (DATETIME)
```

**clients**
```sql
- id (PRIMARY KEY)
- source_id (FOREIGN KEY) - assigned source (required)
- filter_id (FOREIGN KEY) - assigned filter (optional, one filter per client)
- username (VARCHAR, UNIQUE) - client credential
- password (VARCHAR) - client credential
- name (VARCHAR) - friendly name
- email (VARCHAR)
- expiry_date (DATETIME)
- is_active (BOOLEAN)
- max_connections (INT)
- created_at (DATETIME)
- last_login (DATETIME)
- notes (TEXT)
```

**filters**
```sql
- id (PRIMARY KEY)
- name (VARCHAR)
- description (TEXT)
- filter_config (TEXT) - YAML format with two sections: "rules" (include/exclude) and "favoris" (separate)
- created_at (DATETIME)
- updated_at (DATETIME)
```

**live_streams**
```sql
- id (PRIMARY KEY)
- source_id (FOREIGN KEY)
- stream_id (INT) - original stream ID from source
- num (INT) - sequence number from source
- name (VARCHAR)
- stream_type (VARCHAR)
- stream_icon (VARCHAR)
- epg_channel_id (VARCHAR)
- category_id (INT) - primary category
- category_ids (TEXT) - JSON array of all category IDs: [1363,1364]
- added (INT) - Unix timestamp when added to source
- is_adult (BOOLEAN) - adult content flag
- custom_sid (VARCHAR)
- tv_archive (BOOLEAN) - catchup/timeshift available
- tv_archive_duration (INT) - catchup duration in days
- direct_source (VARCHAR)
- stream_url (TEXT) - constructed URL template
- labels (TEXT) - extracted labels (comma-separated: "ESPN,HD,Sports,USA,live")
- is_active (BOOLEAN)
- created_at (DATETIME)
- updated_at (DATETIME)
```

**vod_streams**
```sql
- id (PRIMARY KEY)
- source_id (FOREIGN KEY)
- stream_id (INT)
- num (INT) - sequence number from source
- name (VARCHAR)
- stream_type (VARCHAR) - typically "movie"
- stream_icon (VARCHAR)
- category_id (INT) - primary category
- category_ids (TEXT) - JSON array of all category IDs
- added (INT) - Unix timestamp when added to source
- is_adult (BOOLEAN) - adult content flag
- container_extension (VARCHAR) - mp4, mkv, avi, etc.
- custom_sid (VARCHAR)
- direct_source (VARCHAR)
- stream_url (TEXT)
- plot (TEXT)
- cast (TEXT)
- director (VARCHAR)
- genre (VARCHAR)
- release_date (VARCHAR)
- rating (DECIMAL)
- rating_5based (DECIMAL)
- tmdb (VARCHAR) - TMDB ID for external reference
- trailer (VARCHAR) - trailer URL or ID
- year (INT)
- duration_secs (INT)
- duration (VARCHAR) - formatted duration
- video (TEXT) - JSON with video codec info
- audio (TEXT) - JSON with audio codec info
- bitrate (INT)
- labels (TEXT) - extracted labels (comma-separated: "Action,English,movie")
- is_active (BOOLEAN)
- created_at (DATETIME)
- updated_at (DATETIME)
```

**series**
```sql
- id (PRIMARY KEY)
- source_id (FOREIGN KEY)
- series_id (INT)
- num (INT) - sequence number from source
- name (VARCHAR)
- cover (VARCHAR)
- plot (TEXT)
- cast (TEXT)
- director (VARCHAR)
- genre (VARCHAR)
- release_date (VARCHAR)
- last_modified (INT) - Unix timestamp
- rating (DECIMAL)
- rating_5based (DECIMAL)
- tmdb (VARCHAR) - TMDB ID for external reference
- category_id (INT) - primary category
- category_ids (TEXT) - JSON array of all category IDs
- backdrop_path (TEXT) - JSON array of backdrop images
- youtube_trailer (VARCHAR)
- episode_run_time (INT) - minutes
- seasons (TEXT) - JSON array of season data
- episodes (TEXT) - JSON object with all episodes data
- labels (TEXT) - extracted labels (comma-separated: "Drama,2024,series")
- is_active (BOOLEAN)
- created_at (DATETIME)
- updated_at (DATETIME)
```

**categories**
```sql
- id (PRIMARY KEY)
- source_id (FOREIGN KEY)
- category_id (INT)
- category_name (VARCHAR)
- category_type (ENUM: live, vod, series)
- parent_id (INT)
- labels (TEXT) - extracted labels (comma-separated: "Sports,HD,live")
- created_at (DATETIME)
```

**sync_logs**
```sql
- id (PRIMARY KEY)
- source_id (FOREIGN KEY)
- sync_type (ENUM: live_categories, live_streams, vod_categories, vod_streams, series_categories, series)
- started_at (DATETIME)
- completed_at (DATETIME)
- status (ENUM: success, failed, partial)
- items_added (INT)
- items_updated (INT)
- items_deleted (INT)
- error_message (TEXT)
```

**connection_logs**
```sql
- id (PRIMARY KEY)
- client_id (FOREIGN KEY)
- action (VARCHAR) - API action called
- ip_address (VARCHAR)
- user_agent (VARCHAR)
- created_at (DATETIME)
```

### APIs and Integrations

**Upstream Xtream Codes API (consumed):**
```
GET /player_api.php?username=X&password=Y
GET /player_api.php?username=X&password=Y&action=get_live_categories
GET /player_api.php?username=X&password=Y&action=get_live_streams
GET /player_api.php?username=X&password=Y&action=get_live_streams&category_id=X
GET /player_api.php?username=X&password=Y&action=get_vod_categories
GET /player_api.php?username=X&password=Y&action=get_vod_streams
GET /player_api.php?username=X&password=Y&action=get_vod_streams&category_id=X
GET /player_api.php?username=X&password=Y&action=get_series_categories
GET /player_api.php?username=X&password=Y&action=get_series
GET /player_api.php?username=X&password=Y&action=get_series&category_id=X
GET /player_api.php?username=X&password=Y&action=get_series_info&series_id=X
GET /player_api.php?username=X&password=Y&action=get_simple_data_table&stream_id=X
GET /player_api.php?username=X&password=Y&action=get_short_epg&stream_id=X&limit=X
GET /xmltv.php?username=X&password=Y
```

**Xtream Codes API (provided to clients):**
```
GET /player_api.php?username=X&password=Y
GET /player_api.php?username=X&password=Y&action=get_live_categories
GET /player_api.php?username=X&password=Y&action=get_live_streams
GET /player_api.php?username=X&password=Y&action=get_live_streams&category_id=X
GET /player_api.php?username=X&password=Y&action=get_vod_categories
GET /player_api.php?username=X&password=Y&action=get_vod_streams
GET /player_api.php?username=X&password=Y&action=get_vod_streams&category_id=X
GET /player_api.php?username=X&password=Y&action=get_series_categories
GET /player_api.php?username=X&password=Y&action=get_series
GET /player_api.php?username=X&password=Y&action=get_series&category_id=X
GET /player_api.php?username=X&password=Y&action=get_series_info&series_id=X
GET /player_api.php?username=X&password=Y&action=get_simple_data_table&stream_id=X (proxied - no filtering)
GET /player_api.php?username=X&password=Y&action=get_short_epg&stream_id=X&limit=X (proxied - no filtering)
GET /xmltv.php?username=X&password=Y (proxied - filtered on-the-fly to client's streams)
GET /xmltv.php?username=X&password=Y&stream_id=X (proxied - no filtering)
GET /live/{username}/{password}/{stream_id}.{ext} (any extension: m3u8, ts, etc.)
GET /movie/{username}/{password}/{stream_id}.{ext} (any extension: mp4, mkv, avi, etc.)
GET /series/{username}/{password}/{stream_id}.{ext} (any extension: mp4, mkv, avi, etc.)
```

**Admin REST API (for React frontend):**
```
POST /api/auth/login - Admin authentication
POST /api/auth/logout - Admin logout
GET /api/auth/me - Get current admin user

GET /api/sources - List all sources
POST /api/sources - Add source
GET /api/sources/{id} - Get source details
PUT /api/sources/{id} - Update source
DELETE /api/sources/{id} - Delete source
POST /api/sources/{id}/sync - Trigger manual sync
POST /api/sources/{id}/test - Test source connection

GET /api/clients - List clients (with pagination, filters)
POST /api/clients - Add client
GET /api/clients/{id} - Get client details
PUT /api/clients/{id} - Update client
DELETE /api/clients/{id} - Delete client
GET /api/clients/{id}/logs - Get client connection logs

GET /api/filters - List filters
POST /api/filters - Create filter
GET /api/filters/{id} - Get filter details
PUT /api/filters/{id} - Update filter
DELETE /api/filters/{id} - Delete filter
POST /api/filters/{id}/preview - Preview filter results

GET /api/dashboard/activity - Recent activity
GET /api/sync/status - All sources sync status
GET /api/sync/logs - Sync history
```

### Technology Stack Summary

**Backend:**
- PHP 8.1+
- Slim Framework 4 or vanilla PHP with routing
- PDO for database abstraction
- Guzzle for HTTP client
- Monolog for logging

**Frontend:**
- React 18+
- TypeScript
- Material-UI or Ant Design
- Redux Toolkit or Zustand
- React Router 6
- Axios
- React Hook Form

**Database:**
- MySQL 8.0+ OR SQLite 3 (configurable)
- Migration system (Phinx or custom)

**DevOps:**
- Docker & Docker Compose
- Nginx
- GitHub Actions
- Multi-stage Dockerfile
- Docker Hub / GitHub Container Registry

**Development:**
- Composer (PHP dependencies)
- NPM/Yarn (React dependencies)
- PHPUnit (PHP testing)
- Jest (React testing)
- ESLint + Prettier (code formatting)
- PHPStan (static analysis)

## Development Roadmap

### Phase 1: Core Backend & Database Foundation
**Scope:** Database schema, basic data synchronization, core data models

**Features:**
- Database abstraction layer supporting MySQL and SQLite
- Configuration system for database selection
- Database migration scripts for both DB types (including labels column)
- Source model and storage
- Xtream Codes API client (PHP)
- Basic authentication for sources
- **Separate sync tasks:**
  - Sync live categories task
  - Sync live streams task
- **Label extraction engine** (parse channel/category names by `-`, `|`, `[]`, add stream_type)
- Store live categories and streams in database with extracted labels
- Sync logging per task type (sync_type field in sync_logs)
- Command-line sync script (PHP CLI) with task separation
- Environment configuration (.env)

**Deliverables:**
- Database schema for both MySQL and SQLite
- PHP classes for data models
- API client library for Xtream Codes
- CLI sync tool
- Configuration system
- Docker setup for backend

**Success Criteria:**
- Can connect to Xtream source
- Can fetch and store live categories (separate task)
- Can fetch and store live streams (separate task)
- Each task is logged independently in sync_logs with sync_type
- Data persists in chosen database
- Can switch between MySQL and SQLite via config
- Works in Docker container

### Phase 2: Xtream API Implementation for Clients
**Scope:** Implement Xtream Codes API endpoints for client access

**Features:**
- `player_api.php` endpoint handler
- Client authentication (username/password validation)
- Client-to-source assignment (one source per client)
- `get_live_categories` implementation (from database)
- `get_live_streams` implementation with optional category_id (from database)
- EPG endpoints (get_simple_data_table, get_short_epg) - direct proxy to source
- XMLTV endpoint with on-the-fly filtering
- Basic filter application (show all from client's source)
- Stream URL proxying/redirect to client's source
- JSON response formatting (Xtream format)
- Nginx configuration for URL rewriting

**Deliverables:**
- Working Xtream API endpoints
- Client authentication system
- Stream proxy functionality
- Client table with source assignment
- Nginx configuration
- Docker container for backend

**Success Criteria:**
- IPTV player can connect and authenticate
- IPTV player can list channels from client's assigned source
- IPTV player can play streams
- Streams work through proxy

### Phase 3: React Admin Panel - Foundation & Authentication
**Scope:** React application setup, authentication, layout

**Features:**
- React project setup with TypeScript and Vite
- Material-UI or Ant Design integration
- Admin authentication UI (login page)
- JWT or session-based auth
- Protected routes
- Main layout with sidebar navigation
- Dashboard skeleton
- Logout functionality
- Responsive design
- Dark/light theme toggle

**Deliverables:**
- React application structure
- Authentication flow
- Admin login API endpoint
- Layout components
- Docker container for frontend
- Nginx configuration for React SPA

**Success Criteria:**
- Admin can log in via React UI
- Protected routes redirect to login
- Clean, professional UI layout
- Works in Docker container
- Responsive on mobile/tablet

### Phase 4: React Admin Panel - Source Management
**Scope:** Complete source management interface

**Features:**
- Sources list page with data table
- Add source form/modal
- Edit source form/modal
- Delete source with confirmation
- Test source connection button (real-time feedback)
- Manual sync trigger button (trigger all tasks or individual task types)
- View sync status and progress per task type
- Sync logs display per task type (live_categories, live_streams, vod_categories, vod_streams, series_categories, series)
- Form validation
- Error handling and toast notifications
- Loading states

**Deliverables:**
- Source CRUD components
- Source API endpoints (backend)
- Test connection functionality
- Manual sync trigger
- Real-time sync status updates

**Success Criteria:**
- Can add/edit/delete sources via React UI
- Can test connection with instant feedback
- Can trigger sync (all tasks or individual task types) and see progress
- Sync status shows progress per task type (live_categories, live_streams, etc.)
- Clean, intuitive UI with proper feedback

### Phase 5: React Admin Panel - Client Management
**Scope:** Complete client management interface

**Features:**
- Clients list page with data table
- Add client form/modal
- Edit client form/modal
- Delete client with confirmation
- Source assignment dropdown (required field)
- Filter assignment dropdown (optional field, one filter per client)
- Generate random credentials button
- Enable/disable client toggle
- Expiry date picker
- View client connection logs
- Copy credentials to clipboard
- Display client connection URL
- Pagination and search

**Deliverables:**
- Client CRUD components
- Client API endpoints (backend)
- Connection logging display
- Credential generation

**Success Criteria:**
- Can create clients via React UI
- Must assign source to each client (required)
- Can optionally assign one filter to each client
- Can disable/enable clients
- Client credentials work with IPTV players
- Can view client activity

### Phase 6: Filter System Implementation
**Scope:** Filter creation, management, and application

**Features:**
- Filter definition storage (database - YAML in TEXT field)
- Filter CRUD interface in React
- **Filter structure with two sections:**
  - **rules section:** include/exclude rules
    - Each rule has: name, type (include/exclude), match criteria
    - Match by categories: by_name or by_labels
    - Match by channels: by_name or by_labels
  - **favoris section:** favoris rules (separate field)
    - Each favoris has: name, target_group, match criteria
    - Match by categories: by_name or by_labels
    - Match by channels: by_name or by_labels
- Filter rule editor UI (YAML format with syntax highlighting and validation)
- Filter assignment to clients (one filter per client)
- Filter application logic in API responses:
  - **Stream Filtering Algorithm (for each stream):**
    1. Iterate through all rules in the **rules section** (include/exclude only)
    2. For each rule, check if stream matches (by name or labels, for categories or channels)
    3. **If rule type is "include" and matches:** accept stream
    4. **If rule type is "exclude" and matches:** reject stream (skip to next stream)
    5. Continue checking remaining rules for the stream
    6. After processing all include/exclude rules, check **favoris section separately**
    7. For each favoris, check if stream matches
    8. **If favoris matches:** add stream to that favoris category (with calculated ID)
    9. Note: A stream can appear in both its original category AND in multiple favoris categories

  - **Generate virtual categories from favoris section:**
    - Iterate through all favoris in the **favoris section** (in order)
    - Assign category_id starting from 100000, incrementing for each favoris
    - Create virtual category for each favoris with target_group as category_name
    - Process separately for live/movies/series based on API endpoint
    - When client requests `get_live_categories`, return favoris virtual categories (100000+) **first**, then regular categories
    - When client requests `get_vod_categories`, return favoris virtual categories **first**, then regular categories
    - When client requests `get_series_categories`, return favoris virtual categories **first**, then regular categories
    - When client requests streams by favoris category_id (e.g., 100000), return all streams that matched that favoris

  - Match on name (partial string matching) or labels (exact match)
- Filter preview functionality (show what client will see with virtual categories)
- Filter templates/presets (YAML files)
- Import/export filters (YAML format)

**Deliverables:**
- Filter management components
- Filter builder UI
- Filter application engine (backend)
- Preview functionality
- Client-filter assignment UI

**Success Criteria:**
- Can create filters using YAML format
- YAML editor validates and highlights syntax
- Can assign filters to clients
- Client API responses respect filters from assigned source
- Preview shows accurate results

### Phase 7: VOD & Series Support
**Scope:** Extend functionality to VOD movies and series

**Features:**
- **Separate VOD sync tasks:**
  - Sync VOD categories task
  - Sync VOD streams task
- **Separate series sync tasks:**
  - Sync series categories task
  - Sync series task (with episodes)
- `get_vod_categories` API endpoint
- `get_vod_streams` API endpoint
- `get_series_categories` API endpoint
- `get_series` API endpoint
- `get_series_info` API endpoint
- VOD stream URL proxying
- Series episode URL proxying
- Filters for VOD and series
- VOD/series display in React admin
- Enhanced sync worker to handle all 6 sync task types (live_categories, live_streams, vod_categories, vod_streams, series_categories, series)

**Deliverables:**
- Full Xtream API compatibility
- VOD and series database tables
- VOD/series sync functionality
- VOD/series filtering
- VOD/series UI in admin panel

**Success Criteria:**
- VOD content syncs from sources
- Series content syncs with episodes
- Clients can browse and play VOD
- Clients can browse and play series
- Filters work for all content types

### Phase 8: Automated Sync & Background Workers
**Scope:** Background workers and scheduled synchronization (no cron required)

**Features:**
- **PHP Daemon Worker** (primary method):
  - Long-running PHP process in Docker container
  - Continuous loop checking next_sync timestamps
  - **Processes 6 separate sync tasks per source:**
    1. Live categories sync
    2. Live streams sync
    3. VOD categories sync
    4. VOD streams sync
    5. Series categories sync
    6. Series sync
  - Sleep between iterations (configurable, default 5 minutes)
  - Docker restart policy for reliability

- **Webhook API Endpoint** (alternative method):
  - Authenticated HTTP endpoint to trigger sync
  - Can trigger all tasks or specific task type
  - Can be called by external cron services (cron-job.org, etc.)
  - Can be triggered manually from admin panel

- **On-Demand Sync with TTL** (fallback method):
  - Check data age on API requests per task type
  - Trigger background sync if stale
  - Non-blocking for client requests

- Configurable sync interval per source
- **Task-level sync management:**
  - Each of the 6 task types tracked separately in sync_logs
  - Sync lock per task type (prevent duplicate syncs)
- Incremental sync (detect changes)
- Sync queue system (handle multiple sources and task types)
- Error handling and logging per task
- Email/webhook notifications on sync failure
- Sync history and detailed logs per task type
- Sync progress tracking (real-time in UI) per task
- Manual vs automatic sync tracking
- Sync worker Docker container with restart policy

**Deliverables:**
- PHP daemon worker script
- Webhook API endpoint for external triggers
- On-demand sync middleware
- Sync scheduling system
- Notification system
- Detailed logging
- Background worker container
- Real-time sync progress in React UI
- Documentation for external cron service setup

**Success Criteria:**
- Sources sync automatically at configured intervals without cron
- Can configure custom intervals per source
- Worker automatically restarts on failure
- Receives notifications on failures
- Sync completes without manual intervention
- React UI shows real-time sync progress
- Multiple sync methods available for different environments

### Phase 9: Docker & Deployment
**Scope:** Production-ready Docker setup and deployment

**Features:**
- Multi-stage Dockerfile for backend (optimized size)
- Multi-stage Dockerfile for frontend (optimized build)
- Docker Compose for full stack
- Separate compose file for MySQL vs SQLite
- Environment variable configuration
- Volume mounts for persistent data
- Health checks for all containers
- Container networking and security
- docker-compose.yml with all services
- .env.example for configuration
- README with deployment instructions
- Database initialization scripts

**Deliverables:**
- Production Dockerfiles
- Docker Compose configurations
- Volume management
- Environment templates
- Deployment documentation

**Success Criteria:**
- Can deploy entire stack with `docker-compose up`
- Can choose MySQL or SQLite via environment variable
- Data persists across container restarts
- Health checks work correctly
- Easy configuration via .env file

### Phase 10: CI/CD with GitHub Actions
**Scope:** Automated build, test, and deployment pipeline

**Features:**
- GitHub Actions workflow for CI
- Automated testing (PHPUnit + Jest)
- Code quality checks (PHPStan, ESLint, Prettier)
- Docker image build workflow
- Multi-platform builds (linux/amd64, linux/arm64)
- Semantic versioning and git tagging
- Push to Docker Hub
- Push to GitHub Container Registry
- Automated changelog generation
- Release artifact creation
- Build on pull request (test only)
- Deploy on merge to main (build + push)

**Deliverables:**
- `.github/workflows/ci.yml` - Test and lint
- `.github/workflows/build.yml` - Docker build and push
- `.github/workflows/release.yml` - Release management
- Version tagging automation
- Docker Hub integration
- Documentation for CI/CD

**Success Criteria:**
- Tests run automatically on PRs
- Docker images build on commits to main
- Images pushed to Docker Hub and GHCR
- Multi-platform images available
- Semantic versioning applied automatically
- Releases created with changelogs

## Logical Dependency Chain

**Foundation Layer (Must build first):**
1. Database abstraction layer (MySQL/SQLite support)
2. Database schema and migrations
3. Configuration management (environment variables)
4. Basic PHP project structure
5. Docker backend container setup

**Data Sync Layer (Core data pipeline):**
6. Xtream API client library (HTTP requests)
7. Sync task separation system (6 task types)
8. Live categories fetch and parse (task 1)
9. Live streams fetch and parse (task 2)
10. Live categories and streams database storage
11. Sync logging with sync_type tracking
12. Source model and CRUD operations
13. Basic sync script (CLI) with task separation

**Client API Layer (External interface):**
14. Client model with source assignment (one-to-one)
15. Client authentication system
16. `player_api.php` routing and handler
17. `get_live_streams` endpoint with source-based filtering
18. `get_live_categories` endpoint
19. Stream URL proxy/redirect to client's source
20. JSON response formatter
21. Nginx configuration for Xtream URLs

**React Foundation (Admin interface base):**
22. React project setup (TypeScript, Vite)
23. UI library integration (Material-UI/Ant Design)
24. React Router setup
25. Admin authentication UI (login page)
26. Backend auth API (JWT/session)
27. Protected routes
28. Main layout with navigation
29. Frontend Docker container
30. Docker Compose for backend + frontend

**Source Management (First admin feature):**
31. Sources API endpoints (backend)
32. Sources list component
33. Add/edit source forms
34. Source test connection
35. Manual sync trigger from React UI (per task type)
36. Sync status display per task type

**Client Management (Second admin feature):**
37. Clients API endpoints (backend)
38. Clients list component
39. Add/edit client forms with source dropdown
40. Client enable/disable toggle
41. Connection logging
42. Credential generation and display

**Filter System (Core business logic):**
43. Filter database schema
44. Filter model and storage
45. Filter API endpoints (backend)
46. Filter CRUD components (React)
47. Filter rule builder UI (rules and favoris sections)
48. Filter application logic (backend)
49. Client-filter assignment
50. Filter preview functionality

**Content Expansion (All content types with task separation):**
51. VOD database schema
52. VOD categories sync task implementation
53. VOD streams sync task implementation
54. VOD API endpoints
55. Series database schema
56. Series categories sync task implementation
57. Series sync task implementation
58. Series API endpoints

**Automation & Background Jobs (no cron required):**
59. PHP daemon worker (long-running process)
60. Task separation logic for 6 sync types
61. Webhook API endpoint for external triggers (per task type)
62. On-demand sync middleware with TTL per task
63. Sync lock mechanism per task type
64. Scheduled sync system (loop-based) handling all task types
65. Error notification system per task
66. Sync logging and history per task type
67. Real-time sync progress in UI per task
68. Docker restart policy for worker

**Deployment & CI/CD:**
69. Production Docker Compose
70. Environment configuration templates
71. GitHub Actions for testing
72. GitHub Actions for Docker builds
73. Multi-platform build support
74. Docker Hub/GHCR publishing
75. Release automation
76. Per-task sync status dashboard

## Risks and Mitigations

### Technical Challenges

**Risk:** Upstream Xtream API may be slow or unreliable
- **Mitigation:** Implement timeout handling, async sync jobs, health monitoring per source, error logging

**Risk:** Large catalogs (50,000+ streams) may cause slow sync
- **Mitigation:** Incremental sync, pagination, batch inserts, database indexing, progress tracking

**Risk:** Filter logic may become complex and slow with large datasets
- **Mitigation:** Database-level filtering using SQL WHERE clauses, proper indexes, caching, query optimization

**Risk:** React build size may be too large
- **Mitigation:** Code splitting, lazy loading routes, tree shaking, production optimizations, Vite/Webpack optimization

**Risk:** Docker images may be too large
- **Mitigation:** Multi-stage builds, Alpine base images, minimize layers, .dockerignore, production dependencies only

**Risk:** Database incompatibility between MySQL and SQLite
- **Mitigation:** Use standard SQL, PDO abstraction, test both databases, avoid DB-specific features

**Risk:** One-source-per-client limitation may not suit all users
- **Mitigation:** Document architecture clearly, ensure filters provide enough flexibility, consider future enhancement if demand exists

### MVP Definition & Scope

**Minimum Viable Product Must Include:**
- Single source synchronization (live streams only)
- Basic client authentication with source assignment
- Xtream API for live streams (categories + streams)
- Stream URL proxying to client's assigned source
- React admin panel for source and client management
- Manual sync trigger
- Basic category-based filters
- Docker Compose deployment
- SQLite support for easy installation

**Can Be Deferred to Post-MVP:**
- VOD and series support
- Advanced filter rules (AND/OR combinations)
- Automatic scheduled sync (PHP daemon worker)
- External webhook triggers for sync
- Email/webhook notifications
- Connection logging
- Multi-platform Docker builds
- CI/CD pipeline (can be added after core works)

**MVP Success Criteria:**
- Admin can deploy with `docker-compose up`
- Admin can add Xtream source and sync data
- Admin can create client and assign to source
- Client can connect with IPTV player
- Client can see filtered channel list from assigned source
- Client can play channels
- Admin can apply basic filters
- React UI is functional and professional

**Fast Path to Working Product:**
1. Backend: Database + Source sync (CLI works)
2. Backend: Client API (IPTV player works)
3. React: Login + Source management (can add sources)
4. React: Client management (can create clients)
5. **At this point have working end-to-end system**
6. Add filters for content control
7. Add VOD/series support
8. Add automation and polish

### Deployment & Operations

**Risk:** Docker deployment complexity for non-technical users
- **Mitigation:** Clear documentation, docker-compose.yml with comments, .env templates, video tutorials, one-command setup

**Risk:** Database migrations may fail during updates
- **Mitigation:** Backup automation, migration rollback support, pre-migration validation, testing in CI

**Risk:** GitHub Actions may consume too many minutes
- **Mitigation:** Optimize build steps, cache dependencies, conditional workflows, use self-hosted runners if needed

**Risk:** Multi-platform builds take too long
- **Mitigation:** Parallel build jobs, Docker Buildx caching, build only on releases not every commit

## Appendix

### Technical Specifications

**Xtream Codes API Format Examples:**

**Authentication Response:**
```json
{
  "user_info": {
    "username": "client123",
    "password": "pass123",
    "status": "Active",
    "exp_date": "1735689600",
    "is_trial": "0",
    "max_connections": "1"
  },
  "server_info": {
    "url": "http://your-proxy.com",
    "port": "80",
    "https_port": "443"
  }
}
```

**Live Streams Response Example:**
```json
[
  {
    "num": 1,
    "name": "##### GENERAL #####",
    "stream_type": "live",
    "stream_id": 845453,
    "stream_icon": "http://51.158.145.100/picons/logos/france/845453.png",
    "epg_channel_id": "",
    "added": "1675464765",
    "is_adult": 0,
    "category_id": "1363",
    "category_ids": [1363],
    "custom_sid": null,
    "tv_archive": 0,
    "direct_source": "",
    "tv_archive_duration": 0
  }
]
```

**Note:** All fields from Xtream API are stored in database. The `stream_url` field is constructed by the proxy during sync.

**VOD Streams Response Example:**
```json
[
  {
    "num": 1,
    "name": "AR - Violent Ends (2025)",
    "stream_type": "movie",
    "stream_id": 1328644,
    "stream_icon": "https://image.tmdb.org/t/p/w600_and_h900_bestv2/9BrXyyrd5amNmpbZNc4gSblt6kQ.jpg",
    "rating": "5.857",
    "rating_5based": 2.9,
    "tmdb": "1211776",
    "trailer": "",
    "added": "1764452400",
    "is_adult": 0,
    "category_id": "1487",
    "category_ids": [1487],
    "container_extension": "mp4",
    "custom_sid": null,
    "direct_source": ""
  }
]
```

**Note:** Additional VOD metadata (plot, cast, director, genre, year, duration, video/audio codecs) may be available from `get_vod_info` API call.

**Series Response Example:**
```json
[
  {
    "num": 1,
    "name": "AR - Davey & Jonesie's Locker (2024)",
    "series_id": 25703,
    "cover": "https://image.tmdb.org/t/p/w600_and_h900_bestv2/ezbU23Ayb3FVkfXCAvqpB2sfGKE.jpg",
    "plot": "Best friends Davey and Jonesie escape their bland high school lives through their locker, which is actually a portal to other universes.",
    "cast": "Veronika Slowikowska, Jaelynn Thora Brooks, Sydney Topliffe",
    "director": "",
    "genre": "Comedy / Family",
    "releaseDate": "2024-03-22",
    "release_date": "2024-03-22",
    "last_modified": "1721497059",
    "rating": "4",
    "rating_5based": "2",
    "backdrop_path": [
      "https://image.tmdb.org/t/p/w1280/ohjylMcBzzPYIq16tMAga3kBvF1.jpg",
      "https://image.tmdb.org/t/p/w1280/ftExofLUyY4SDPfGNEJGuJWLH1w.jpg"
    ],
    "youtube_trailer": "LSPzvzgZ9JE",
    "tmdb": "246680",
    "episode_run_time": "0",
    "category_id": "956",
    "category_ids": [956]
  }
]
```

**Note:** The series list from `get_series` doesn't include `seasons` and `episodes` data. To get full episode information, use `get_series_info` with the `series_id`.

**Filter YAML Format Example:**
```yaml
# Filter configuration in YAML format
# Two separate sections: rules (include/exclude) and favoris

# Include/Exclude Rules Section
rules:
  # Rule 1: Exclude adult content
  - name: "Block Adult Content"
    type: exclude
    match:
      categories:
        by_name: ["Adult", "XXX", "18+"]
        by_labels: ["Adult", "18+"]
      channels:
        by_name: ["Playboy TV", "Adult Channel"]
        by_labels: ["Adult", "XXX"]

  # Rule 2: Include only sports channels
  - name: "Include Sports"
    type: include
    match:
      categories:
        by_name: ["Sports", "Football", "Basketball"]
        by_labels: ["Sports", "ESPN"]
      channels:
        by_labels: ["Sports", "HD"]

  # Rule 3: Exclude news channels
  - name: "Block News"
    type: exclude
    match:
      categories:
        by_name: ["News", "Politics"]
      channels:
        by_labels: ["News"]

# Favoris Section (separate from rules)
favoris:
  # Favoris 1: Kids channels (will get category_id: 100000)
  - name: "Kids Favorites"
    target_group: "Kids Corner"
    match:
      categories:
        by_name: ["Cartoons", "Kids", "Children"]
        by_labels: ["Kids", "Cartoon"]
      channels:
        by_name: ["Disney", "Nickelodeon", "Cartoon Network"]
        by_labels: ["Kids", "Family"]

  # Favoris 2: HD sports (will get category_id: 100001)
  - name: "HD Sports Favorites"
    target_group: "My Sports HD"
    match:
      channels:
        by_labels: ["Sports", "HD", "FHD"]

  # Favoris 3: Movies (will get category_id: 100002)
  - name: "My Movies"
    target_group: "Favorite Movies"
    match:
      categories:
        by_name: ["Action", "Comedy"]
```

### Environment Configuration

**Backend .env:**
```bash
# Database Configuration
DB_TYPE=mysql # or sqlite
DB_HOST=mysql
DB_PORT=3306
DB_NAME=iptv_proxy
DB_USER=iptv_user
DB_PASS=secure_password
DB_SQLITE_PATH=/app/data/database.sqlite

# Application
APP_ENV=production
APP_DEBUG=false
APP_URL=http://localhost

# Admin Credentials
ADMIN_USERNAME=admin
ADMIN_PASSWORD=changeme123

# Sync Configuration (no cron required)
SYNC_ENABLED=true
DEFAULT_SYNC_INTERVAL=24  # Hours between syncs per source
SYNC_CHECK_INTERVAL=300  # Seconds between daemon checks (5 min)
SYNC_METHOD=daemon  # Options: daemon, webhook, ondemand
SYNC_WEBHOOK_TOKEN=your_secret_token_here  # For webhook authentication

# Logging
LOG_LEVEL=info
```

**Docker Compose:**
```yaml
version: '3.8'

services:
  backend:
    image: ghcr.io/yourorg/iptv-proxy-backend:latest
    environment:
      - DB_TYPE=mysql
      - DB_HOST=mysql
    volumes:
      - ./data:/app/data
    ports:
      - "8080:80"

  frontend:
    image: ghcr.io/yourorg/iptv-proxy-frontend:latest
    ports:
      - "3000:80"
    depends_on:
      - backend

  mysql:
    image: mysql:8.0
    environment:
      - MYSQL_ROOT_PASSWORD=rootpass
      - MYSQL_DATABASE=iptv_proxy
      - MYSQL_USER=iptv_user
      - MYSQL_PASSWORD=secure_password
    volumes:
      - mysql_data:/var/lib/mysql

  sync-worker:
    image: ghcr.io/yourorg/iptv-proxy-backend:latest
    command: php /app/bin/sync-daemon.php  # Long-running daemon (no cron needed)
    restart: always  # Auto-restart on failure
    environment:
      - DB_TYPE=mysql
      - DB_HOST=mysql
      - SYNC_CHECK_INTERVAL=300  # Check every 5 minutes
    depends_on:
      - backend
      - mysql

volumes:
  mysql_data:
```

### GitHub Actions Workflow Examples

**CI Workflow (.github/workflows/ci.yml):**
```yaml
name: CI

on: [pull_request]

jobs:
  test-backend:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - name: Setup PHP
        uses: shivammathur/setup-php@v2
        with:
          php-version: '8.1'
      - name: Install dependencies
        run: composer install
      - name: Run tests
        run: vendor/bin/phpunit

  test-frontend:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - name: Setup Node
        uses: actions/setup-node@v3
        with:
          node-version: '18'
      - name: Install dependencies
        run: npm ci
      - name: Run tests
        run: npm test
```

**Docker Build Workflow (.github/workflows/docker.yml):**
```yaml
name: Docker Build

on:
  push:
    branches: [main]
    tags: ['v*']

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - name: Set up QEMU
        uses: docker/setup-qemu-action@v2
      - name: Set up Docker Buildx
        uses: docker/setup-buildx-action@v2
      - name: Login to DockerHub
        uses: docker/login-action@v2
        with:
          username: ${{ secrets.DOCKERHUB_USERNAME }}
          password: ${{ secrets.DOCKERHUB_TOKEN }}
      - name: Build and push
        uses: docker/build-push-action@v4
        with:
          platforms: linux/amd64,linux/arm64
          push: true
          tags: |
            yourorg/iptv-proxy:latest
            yourorg/iptv-proxy:${{ github.sha }}
```

### Database Indexes for Performance
```sql
CREATE INDEX idx_live_source ON live_streams(source_id);
CREATE INDEX idx_live_category ON live_streams(category_id);
CREATE INDEX idx_live_active ON live_streams(is_active);
CREATE INDEX idx_client_username ON clients(username);
CREATE INDEX idx_client_source ON clients(source_id);
CREATE INDEX idx_client_active ON clients(is_active);
CREATE INDEX idx_filter_client ON client_filters(client_id);

-- Note: For label filtering, use LIKE queries with comma delimiters
-- Example: WHERE labels LIKE '%,HD,%' OR labels LIKE 'HD,%' OR labels LIKE '%,HD'
-- Or use FIND_IN_SET for MySQL: WHERE FIND_IN_SET('HD', labels)
-- Consider full-text search indexes for larger deployments if needed
```

### External Webhook Setup (Alternative to Daemon)

If using webhook-based sync instead of PHP daemon:

**Setup with cron-job.org (Free):**
1. Register at https://cron-job.org
2. Create new cron job pointing to: `https://your-proxy.com/api/sync/webhook`
3. Add header: `Authorization: Bearer YOUR_SYNC_WEBHOOK_TOKEN`
4. Set schedule (e.g., every 24 hours)

**Setup with GitHub Actions:**
```yaml
name: Trigger IPTV Sync
on:
  schedule:
    - cron: '0 0 * * *'  # Daily at midnight
jobs:
  sync:
    runs-on: ubuntu-latest
    steps:
      - name: Trigger sync
        run: |
          curl -X POST https://your-proxy.com/api/sync/webhook \
            -H "Authorization: Bearer ${{ secrets.SYNC_WEBHOOK_TOKEN }}"
```

**Setup with UptimeRobot (Free):**
1. Create HTTP monitor
2. URL: `https://your-proxy.com/api/sync/webhook`
3. Custom HTTP headers: `Authorization: Bearer TOKEN`
4. Interval: Every 24 hours (requires paid plan, or use 5-min checks)

### Technology Requirements

**Backend:**
- PHP 8.1+
- Extensions: pdo, pdo_mysql, pdo_sqlite, curl, json, mbstring, openssl
- Composer 2.x

**Frontend:**
- Node.js 18+
- NPM or Yarn
- Modern browser support (ES6+)

**Infrastructure:**
- Docker 20.10+
- Docker Compose 2.x
</PRD>
