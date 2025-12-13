# Mockoon Mock API Setup for Xtream Codes

Complete mock API setup using **Mockoon** for testing Xtream Codes `player_api.php` endpoints locally and in Docker pipelines.

## Overview

This setup allows you to:
- ✅ Test your Xtream integration without real API calls
- ✅ Run tests locally during development
- ✅ Integrate into CI/CD pipelines (GitHub Actions, GitLab CI, etc.)
- ✅ Simulate different API responses (success, empty, errors)
- ✅ Mock streaming endpoints for memory-efficient testing

## Quick Start

### Local (Development)

```bash
# 1. Open Mockoon desktop app and import collection
# File → Import environment → select mockoon/mockoon-xtream-collection.json
# Click green play button

# 2. Test it works
curl "http://localhost:3000/player_api.php?username=testuser&password=testpass"

# 3. Run tests
cd back && ./vendor/bin/phpunit tests/Feature/Services/Xtream/XtreamClientMockTest.php
```

### Docker (CI/CD)

```bash
# Start mock server
./mockoon/scripts/mockoon.sh start

# Or with docker-compose
docker-compose -f mockoon/docker-compose.yml up -d mockoon-xtream

# Run tests
export XTREAM_BASE_URL=http://localhost:3000
cd back && ./vendor/bin/phpunit tests/Feature/Services/Xtream/
```

---

## What's Included

### Files Created

| File | Purpose |
|------|---------|
| `mockoon-xtream-collection.json` | Complete API mock definition with all endpoints |
| `docker-compose.yml` | Docker setup for running mock in containers |
| `scripts/mockoon.sh` | Helper script to start/stop/manage mock server |
| `.env.testing.example` | Environment configuration template for testing |
| `../back/tests/Feature/Services/Xtream/XtreamClientMockTest.php` | Complete test suite examples |
| `SETUP.md` | Detailed setup guide with all options |
| `QUICKSTART.md` | Fast reference guide |
| `README.md` | This file |

### Mocked Endpoints

All running on base URL: `http://localhost:3000` (or `http://mockoon-xtream:3000` in Docker)

#### 1. Authentication
```
GET /player_api.php?username=testuser&password=testpass
```
Returns user info and server info

#### 2. Live Categories
```
GET /player_api.php?username=testuser&password=testpass&action=get_live_categories
```
Returns array of 5 example categories (News, Sports, Entertainment, Documentary, Kids)

#### 3. Live Streams
```
GET /player_api.php?username=testuser&password=testpass&action=get_live_streams
GET /player_api.php?username=testuser&password=testpass&action=get_live_streams&category_id=1
```
Returns array of example streams, optionally filtered by category

---

## Architecture

```
Development Environment:
┌─────────────────────────────────────────┐
│         Your PHP Application            │
│                                         │
│  - XtreamClient (points to mock)        │
│  - SyncService                          │
│  - Controllers                          │
└─────────────┬───────────────────────────┘
              │
              ↓ HTTP (localhost:3000)
┌─────────────────────────────────────────┐
│      Mockoon Mock Server (Local)        │
│  - Listens on http://localhost:3000     │
│  - Serves mockoon-xtream-collection.json│
│  - Returns mock API responses           │
└─────────────────────────────────────────┘


Docker/CI Environment:
┌──────────────────────┐
│   Test Container     │
│  Your PHP App        │
└──────────┬───────────┘
           │
           ↓ HTTP (mockoon-xtream:3000)
┌──────────────────────────────────────────┐
│   Mockoon Container (Docker)             │
│  - Service name: mockoon-xtream          │
│  - Port: 3000                            │
│  - Volume: mockoon-xtream-collection.json│
└──────────────────────────────────────────┘
```

---

## Configuration

### Environment Variables

**For Local Testing** (`.env.testing`)
```ini
XTREAM_BASE_URL=http://localhost:3000
XTREAM_USERNAME=testuser
XTREAM_PASSWORD=testpass
```

**For Docker Testing**
```ini
XTREAM_BASE_URL=http://mockoon-xtream:3000
XTREAM_USERNAME=testuser
XTREAM_PASSWORD=testpass
```

**For Production** (`.env`)
```ini
XTREAM_BASE_URL=https://your-real-xtream-server.com
XTREAM_USERNAME=real_username
XTREAM_PASSWORD=real_password
```

---

## Usage Examples

### Basic Test
```php
use App\Services\Xtream\XtreamClient;

$client = new XtreamClient([
    'url' => 'http://localhost:3000',
    'username' => 'testuser',
    'password' => 'testpass',
]);

// Get categories
$categories = $client->getLiveCategories();
// Returns: [
//   {'category_id': '1', 'category_name': 'News', 'parent_id': 0},
//   {'category_id': '2', 'category_name': 'Sports', 'parent_id': 0},
//   ...
// ]

// Get all streams
$streams = $client->getLiveStreams();
// Returns: [
//   {'stream_id': '1001', 'name': 'BBC News', 'category_id': '1', ...},
//   {'stream_id': '2001', 'name': 'Sky Sports 1', 'category_id': '2', ...},
//   ...
// ]

// Get streams by category
$newsStreams = $client->getLiveStreams(categoryId: 1);
// Returns only streams where category_id == 1
```

### Streaming (Memory Efficient)
```php
// Use generators to process large datasets without loading all in memory
foreach ($client->streamLiveCategories() as $category) {
    // Process one category at a time
    echo $category['category_name'];
}

foreach ($client->streamLiveStreams() as $stream) {
    // Process one stream at a time
    // Memory usage stays constant regardless of total streams
    process_stream($stream);
}
```

### In Unit Tests
```php
class MyServiceTest extends TestCase
{
    private XtreamClient $client;

    protected function setUp(): void
    {
        parent::setUp();

        $this->client = new XtreamClient([
            'url' => 'http://localhost:3000',
            'username' => 'testuser',
            'password' => 'testpass',
        ]);
    }

    public function testMyService(): void
    {
        $categories = $this->client->getLiveCategories();

        $this->assertIsArray($categories);
        $this->assertNotEmpty($categories);
        $this->assertArrayHasKey('category_id', $categories[0]);
    }
}
```

---

## Management Scripts

### Start/Stop Mock Server

```bash
# Start
./scripts/mockoon.sh start
# Output: ✓ Mockoon server started on http://localhost:3000

# Check status
./scripts/mockoon.sh status
# Output: ✓ Mockoon server is running
#         CONTAINER ID  IMAGE                PORT        STATUS
#         abc123...     mockoon/mockoon...   3000->3000  Up 2 minutes

# View logs
./scripts/mockoon.sh logs
# Output: [Mockoon] Server started on http://0.0.0.0:3000 ...

# Stop
./scripts/mockoon.sh stop
# Output: ✓ Mockoon server stopped
```

### Docker Compose

```bash
# Start mock service
docker-compose -f docker-compose.mockoon.yml up -d mockoon-xtream

# Start with your app
docker-compose -f docker-compose.mockoon.yml up

# Stop
docker-compose -f docker-compose.mockoon.yml down

# View logs
docker-compose -f docker-compose.mockoon.yml logs mockoon-xtream -f
```

---

## Testing Workflow

### Daily Development

```bash
# 1. Start mock server
./scripts/mockoon.sh start

# 2. Run your tests
cd back
./vendor/bin/phpunit tests/Feature/Services/Xtream/

# 3. Develop/modify code
# ... edit files ...

# 4. Run tests again
./vendor/bin/phpunit tests/Feature/Services/Xtream/ -v

# 5. Stop when done
./scripts/mockoon.sh stop
```

### CI/CD Pipeline

```bash
# In GitHub Actions / GitLab CI:

# 1. Start mock server
./scripts/mockoon.sh start

# 2. Run tests
cd back
export XTREAM_BASE_URL=http://localhost:3000
./vendor/bin/phpunit tests/

# 3. Cleanup (automatic in CI)
./scripts/mockoon.sh stop
```

---

## Adding New Endpoints

### Via Mockoon GUI (Recommended)

1. Open Mockoon desktop app
2. Select "Xtream Codes API Mock" environment
3. Click **"+"** icon to add new route
4. Configure:
   - **Endpoint**: `player_api.php`
   - **Method**: GET
   - **Add Response**: Set response body, status code, headers
5. Save (auto-saved)
6. Reload mock server (Ctrl+E)

### Via JSON (Advanced)

Edit `mockoon-xtream-collection.json`:

```json
{
  "type": "route",
  "uuid": "unique-id",
  "endpoint": "player_api.php",
  "method": "get",
  "responses": [
    {
      "uuid": "response-uuid",
      "statusCode": 200,
      "headers": [{"key": "Content-Type", "value": "application/json"}],
      "body": "{\"key\": \"value\"}"
    }
  ]
}
```

---

## Mocking Different Scenarios

### Success Response
Default response returns full data for all endpoints.

### Empty Results
In Mockoon GUI, set response to "No Categories" or "No Streams" for testing empty cases.

### Authentication Failure
In Mockoon GUI, switch to "Auth Failed" response to test auth failures.

### Custom Responses
Add new response variants in Mockoon GUI for:
- Timeout scenarios
- Error responses
- Large datasets
- Edge cases

---

## Running Full Test Suite

```bash
# Start mock
./scripts/mockoon.sh start

# Wait for it to be ready
sleep 2

# Run all Xtream-related tests
cd back
./vendor/bin/phpunit tests/Feature/Services/Xtream/ -v

# Or run all tests
./vendor/bin/phpunit tests/ --coverage-html coverage/

# Stop mock
./scripts/mockoon.sh stop
```

---

## Troubleshooting

### Mock Won't Start
```bash
# Check Docker is running
docker ps

# Check logs
./scripts/mockoon.sh logs

# Try restart
./scripts/mockoon.sh restart

# If port issue, kill existing process
lsof -i :3000
kill -9 <PID>
```

### Tests Can't Connect
```bash
# Verify mock is running
./scripts/mockoon.sh status

# Test endpoint directly
curl "http://localhost:3000/player_api.php?username=testuser&password=testpass"

# Check XTREAM_BASE_URL env var
echo $XTREAM_BASE_URL

# For Docker: use service name instead of localhost
export XTREAM_BASE_URL=http://mockoon-xtream:3000
```

### Import Issue in Mockoon GUI
- Ensure `mockoon-xtream-collection.json` is in project root
- Verify JSON is valid (use JSONLint)
- Try creating environment manually and adding routes

---

## Performance Notes

- **Local startup**: ~2-3 seconds
- **Docker startup**: ~5-10 seconds
- **Mock response latency**: 50-150ms (configurable)
- **Memory usage**: ~50MB per Mockoon instance
- **Streaming**: Handles large datasets with constant memory usage

---

## Documentation

- **Quick Start**: See `QUICKSTART.md`
- **Detailed Guide**: See `SETUP.md`
- **Test Examples**: See `../back/tests/Feature/Services/Xtream/XtreamClientMockTest.php`
- **External Docs**: https://docs.mockoon.com

---

## Summary

| Aspect | Details |
|--------|---------|
| **Mock Server** | Mockoon |
| **Base URL (Local)** | http://localhost:3000 |
| **Base URL (Docker)** | http://mockoon-xtream:3000 |
| **Port** | 3000 |
| **Collection File** | mockoon-xtream-collection.json |
| **Setup Time** | ~5 minutes |
| **Endpoints Mocked** | 3 (auth, get_live_categories, get_live_streams) |
| **Test Examples** | ✓ (in back/tests/Feature/Services/Xtream/) |
| **Docker Support** | ✓ (docker-compose.mockoon.yml) |
| **CI/CD Ready** | ✓ (GitHub Actions, GitLab CI examples) |
| **Production Safe** | ✓ (easily switch with env vars) |

---

## Next Steps

1. **Read**: `MOCKOON_QUICKSTART.md` (5 min)
2. **Setup**: Import collection into Mockoon GUI (2 min)
3. **Test**: Run local tests (5 min)
4. **Docker**: Set up docker-compose (2 min)
5. **CI/CD**: Integrate with your pipeline (varies)

---

**Status**: ✅ Ready to use!

For detailed setup and troubleshooting, see the full guides in this directory.
