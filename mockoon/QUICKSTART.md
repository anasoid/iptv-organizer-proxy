# Mockoon Xtream Codes API Mock - Quick Start Guide

## 5-Minute Setup

### Local Testing (Development)

#### 1. Install & Start
```bash
# Install Mockoon desktop app
brew install mockoon  # macOS
# OR download from https://mockoon.com/download/

# Open Mockoon → File → Import environment
# Select: mockoon/mockoon-xtream-collection.json
# Click green play button to start (Ctrl+E)
```

#### 2. Verify It Works
```bash
curl "http://localhost:3000/player_api.php?username=testuser&password=testpass"
```

#### 3. Run Tests
```bash
cd back
./vendor/bin/phpunit tests/Feature/Services/Xtream/XtreamClientMockTest.php
```

---

### Docker Testing (CI/CD Pipelines)

#### 1. Start Mock & App Together
```bash
docker-compose -f mockoon/docker-compose.yml up -d
```

#### 2. Or Use Helper Script
```bash
# Start
./mockoon/scripts/mockoon.sh start

# Check status
./mockoon/scripts/mockoon.sh status

# View logs
./mockoon/scripts/mockoon.sh logs

# Stop
./mockoon/scripts/mockoon.sh stop
```

#### 3. Run Tests
```bash
export XTREAM_BASE_URL=http://localhost:3000
cd back
./vendor/bin/phpunit tests/Feature/Services/Xtream/
```

---

## What I Created

### Files Added

1. **mockoon-xtream-collection.json** - Complete API mock definition
   - Authentication endpoint
   - get_live_categories endpoint
   - get_live_streams endpoint
   - Multiple response scenarios (success, empty, errors)

2. **docker-compose.yml** - Docker setup for CI/CD
   - Mockoon service on port 3000
   - Your app can depend on it
   - Health checks included

3. **SETUP.md** - Complete detailed guide
   - Local and Docker setup instructions
   - PHP test examples
   - CI/CD integration (GitHub Actions, GitLab CI)
   - Troubleshooting guide

4. **QUICKSTART.md** - This file (fast reference)

5. **.env.testing.example** - Environment config template
   ```ini
   XTREAM_BASE_URL=http://localhost:3000
   XTREAM_USERNAME=testuser
   XTREAM_PASSWORD=testpass
   ```

6. **../back/tests/Feature/Services/Xtream/XtreamClientMockTest.php**
   - Complete test suite showing how to use the mock
   - Tests for all major endpoints
   - Streaming tests
   - Memory efficiency tests

7. **scripts/mockoon.sh** - Convenient control script
   ```bash
   ./mockoon/scripts/mockoon.sh start     # Start mock server
   ./mockoon/scripts/mockoon.sh stop      # Stop mock server
   ./mockoon/scripts/mockoon.sh status    # Check status
   ./mockoon/scripts/mockoon.sh logs      # View logs
   ```

---

## API Endpoints Mocked

| Endpoint | Method | Parameters | Mocked |
|----------|--------|-----------|--------|
| `/player_api.php` (auth) | GET | username, password | ✓ |
| `action=get_live_categories` | GET | - | ✓ |
| `action=get_live_streams` | GET | category_id (optional) | ✓ |

### Example Requests
```bash
# Authentication
curl "http://localhost:3000/player_api.php?username=testuser&password=testpass"

# Get categories
curl "http://localhost:3000/player_api.php?username=testuser&password=testpass&action=get_live_categories"

# Get all streams
curl "http://localhost:3000/player_api.php?username=testuser&password=testpass&action=get_live_streams"

# Get streams by category
curl "http://localhost:3000/player_api.php?username=testuser&password=testpass&action=get_live_streams&category_id=1"
```

---

## Adding More Endpoints

### Option 1: GUI (Recommended)
1. Open Mockoon desktop app
2. Select "Xtream Codes API Mock" environment
3. Click **"+"** to add new route
4. Define endpoint, method, and responses

### Option 2: Edit JSON
Edit `mockoon-xtream-collection.json` directly and reload

---

## Using in Your Code

### PHP Class Method
```php
use App\Services\Xtream\XtreamClient;

// Point to mock server for testing
$client = new XtreamClient([
    'url' => 'http://localhost:3000',
    'username' => 'testuser',
    'password' => 'testpass',
]);

// Use normally
$categories = $client->getLiveCategories();
$streams = $client->getLiveStreams();
```

### In Tests
```php
class MyTest extends TestCase
{
    protected function setUp(): void
    {
        parent::setUp();
        // Mock URL from env or default
        $url = $_ENV['XTREAM_BASE_URL'] ?? 'http://localhost:3000';

        $this->client = new XtreamClient([
            'url' => $url,
            'username' => 'testuser',
            'password' => 'testpass',
        ]);
    }

    public function testSomething(): void
    {
        $result = $this->client->getLiveCategories();
        $this->assertIsArray($result);
    }
}
```

---

## Testing Strategies

### Local Development
- Run Mockoon GUI locally
- Point your code to `http://localhost:3000`
- Test as you develop

### Automated Tests
- Use helper script: `./scripts/mockoon.sh start`
- Run PHPUnit tests
- Stop with: `./scripts/mockoon.sh stop`

### CI/CD Pipeline
- Use Docker Compose in pipeline
- Set `XTREAM_BASE_URL=http://mockoon-xtream:3000`
- Run tests normally

### Production
- Keep `XTREAM_BASE_URL` pointing to real server
- No mock server needed in production

---

## Switching Between Mock & Real API

```php
// .env.testing
XTREAM_BASE_URL=http://localhost:3000

// .env.production
XTREAM_BASE_URL=https://real-xtream.example.com
```

Or in code:
```php
$baseUrl = match(env('APP_ENV')) {
    'testing' => 'http://localhost:3000',
    'production' => env('XTREAM_BASE_URL'),
    default => env('XTREAM_BASE_URL'),
};

$client = new XtreamClient(['url' => $baseUrl]);
```

---

## Common Commands

```bash
# Start mock server (Docker)
./mockoon/scripts/mockoon.sh start

# Check if running
./mockoon/scripts/mockoon.sh status

# View logs
./mockoon/scripts/mockoon.sh logs

# Stop server
./mockoon/scripts/mockoon.sh stop

# Run tests
cd back && ./vendor/bin/phpunit tests/Feature/Services/Xtream/

# Test specific test class
./vendor/bin/phpunit tests/Feature/Services/Xtream/XtreamClientMockTest.php

# Test with debug output
./vendor/bin/phpunit tests/Feature/Services/Xtream/ -v

# Run with coverage report
./vendor/bin/phpunit tests/Feature/Services/Xtream/ --coverage-html=coverage
```

---

## Troubleshooting

### Port 3000 Already in Use
```bash
# Find what's using it
lsof -i :3000

# Kill it
kill -9 <PID>

# Or use different port (requires config change)
```

### Mock Won't Start
```bash
# Check Docker is running
docker ps

# Check logs
./scripts/mockoon.sh logs

# Restart
./scripts/mockoon.sh restart
```

### Tests Can't Connect
```bash
# Verify mock is running
curl http://localhost:3000/player_api.php?username=testuser&password=testpass

# Check XTREAM_BASE_URL in your test
echo $XTREAM_BASE_URL

# For Docker tests, use service name
XTREAM_BASE_URL=http://mockoon-xtream:3000
```

### Still Having Issues?
See **MOCKOON_SETUP.md** for detailed troubleshooting section.

---

## Next Steps

1. ✅ **Import collection** - Open Mockoon, import mockoon-xtream-collection.json
2. ✅ **Start locally** - Click green play button or use `./scripts/mockoon.sh start`
3. ✅ **Test endpoints** - Use curl examples above
4. ✅ **Run test suite** - `./vendor/bin/phpunit tests/Feature/Services/Xtream/XtreamClientMockTest.php`
5. ✅ **Set up Docker** - `docker-compose -f docker-compose.mockoon.yml up`
6. ✅ **Add to CI/CD** - See MOCKOON_SETUP.md for GitHub Actions / GitLab CI examples

---

## Support

- **Mockoon Docs**: https://docs.mockoon.com
- **Mockoon Collection Format**: https://docs.mockoon.com/latest/mockoon-data-files/
- **Docker Setup**: https://docs.mockoon.com/latest/docker/
- **Detailed Guide**: See MOCKOON_SETUP.md in this project

---

**Created**: 2025
**Mock Server**: Mockoon
**API**: Xtream Codes player_api.php
**Status**: Ready to use!
