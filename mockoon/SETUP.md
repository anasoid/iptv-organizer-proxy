# Mockoon Setup Guide - Xtream Codes Mock API

This guide explains how to set up and use Mockoon to mock Xtream Codes API for local testing and CI/CD pipelines.

## What is Mockoon?

Mockoon is a standalone application that creates fake APIs without coding. It allows you to:
- Mock REST API endpoints
- Define response behavior (success/error scenarios)
- Run locally or in Docker
- Use the same mock in development and CI/CD

## Setup Options

### Option 1: Local Setup (For Development)

#### 1.1 Install Mockoon
```bash
# macOS
brew install mockoon

# Linux
sudo apt-get install mockoon

# Or download from https://mockoon.com/download/
```

#### 1.2 Import the Collection
1. Open Mockoon desktop app
2. Go to **File → Import environment**
3. Select `mockoon/mockoon-xtream-collection.json`
4. Click **Create local environment**

#### 1.3 Start the Mock Server
1. Select the "Xtream Codes API Mock" environment
2. Click the **green play button** or press `Ctrl+E` (Cmd+E on Mac)
3. Server will start on `http://localhost:3000`
4. You'll see status: "running"

#### 1.4 Verify It's Working
```bash
# Test authentication endpoint
curl "http://localhost:3000/player_api.php?username=testuser&password=testpass"

# Test get_live_categories endpoint
curl "http://localhost:3000/player_api.php?username=testuser&password=testpass&action=get_live_categories"

# Test get_live_streams endpoint
curl "http://localhost:3000/player_api.php?username=testuser&password=testpass&action=get_live_streams"

# Test with category filter
curl "http://localhost:3000/player_api.php?username=testuser&password=testpass&action=get_live_streams&category_id=1"
```

---

### Option 2: Docker Setup (For CI/CD Pipelines)

#### 2.1 Start Mock Server in Docker

```bash
# Using docker-compose (recommended)
docker-compose -f mockoon/docker-compose.yml up -d mockoon-xtream

# Or using docker directly
docker run -d \
  --name mockoon-xtream-codes \
  -p 3000:3000 \
  -v $(pwd)/mockoon/mockoon-xtream-collection.json:/data/collection.json:ro \
  mockoon/mockoon:latest \
  --data /data/collection.json --host 0.0.0.0 --port 3000
```

#### 2.2 Verify Container is Running
```bash
# Check status
docker ps | grep mockoon

# Test endpoint
curl "http://localhost:3000/player_api.php?username=testuser&password=testpass"

# View logs
docker logs mockoon-xtream-codes
```

#### 2.3 Stop Mock Server
```bash
# Using docker-compose
docker-compose -f mockoon/docker-compose.yml down

# Or using docker directly
docker stop mockoon-xtream-codes
docker rm mockoon-xtream-codes
```

---

## Configuration

### Environment Variables

Set these in your `.env` or test configuration:

```bash
# Local development
XTREAM_BASE_URL=http://localhost:3000

# Docker testing
XTREAM_BASE_URL=http://mockoon-xtream:3000

# Production (keep separate)
XTREAM_BASE_URL=https://real-xtream-server.com
```

### PHP Configuration Example

Create a `.env.testing` file:
```ini
APP_ENV=testing
XTREAM_BASE_URL=http://localhost:3000
XTREAM_USERNAME=testuser
XTREAM_PASSWORD=testpass
```

---

## Using Mock in PHP Tests

### Example 1: Unit Test with Mock

```php
<?php

namespace Tests\Unit\Services\Xtream;

use PHPUnit\Framework\TestCase;
use App\Services\Xtream\XtreamClient;

class XtreamClientTest extends TestCase
{
    private XtreamClient $client;
    private string $mockUrl = 'http://localhost:3000';

    protected function setUp(): void
    {
        parent::setUp();

        // Create client pointing to mock server
        $this->client = new XtreamClient([
            'url' => $this->mockUrl,
            'username' => 'testuser',
            'password' => 'testpass',
        ]);
    }

    public function testGetLiveCategories(): void
    {
        $categories = $this->client->getLiveCategories();

        $this->assertIsArray($categories);
        $this->assertGreaterThan(0, count($categories));
        $this->assertArrayHasKey('category_id', $categories[0]);
        $this->assertArrayHasKey('category_name', $categories[0]);
    }

    public function testGetLiveStreams(): void
    {
        $streams = $this->client->getLiveStreams();

        $this->assertIsArray($streams);
        $this->assertGreaterThan(0, count($streams));
        $this->assertArrayHasKey('stream_id', $streams[0]);
        $this->assertArrayHasKey('name', $streams[0]);
    }

    public function testGetLiveStreamsByCategory(): void
    {
        $streams = $this->client->getLiveStreams(categoryId: 1);

        $this->assertIsArray($streams);

        // Should return only News streams (category_id=1)
        foreach ($streams as $stream) {
            $this->assertEquals('1', $stream['category_id']);
        }
    }

    public function testAuthentication(): void
    {
        $userInfo = $this->client->getUserInfo();

        $this->assertEquals('testuser', $userInfo['user_info']['username']);
        $this->assertEquals(1, $userInfo['user_info']['auth']);
        $this->assertTrue(isset($userInfo['server_info']));
    }
}
```

### Example 2: Integration Test with Streaming

```php
<?php

namespace Tests\Feature\Services\Xtream;

use PHPUnit\Framework\TestCase;
use App\Services\Xtream\XtreamClient;

class XtreamStreamingTest extends TestCase
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

    public function testStreamCategoriesAsGenerator(): void
    {
        $categories = $this->client->streamLiveCategories();

        $count = 0;
        foreach ($categories as $category) {
            $count++;
            $this->assertArrayHasKey('category_id', $category);
            $this->assertArrayHasKey('category_name', $category);
        }

        $this->assertGreaterThan(0, $count);
    }

    public function testStreamLiveStreamsAsGenerator(): void
    {
        $streams = $this->client->streamLiveStreams();

        $count = 0;
        foreach ($streams as $stream) {
            $count++;
            $this->assertArrayHasKey('stream_id', $stream);
            $this->assertArrayHasKey('name', $stream);
        }

        $this->assertGreaterThan(0, $count);
    }

    public function testLargeDatasetHandling(): void
    {
        // Test that streaming works correctly with multiple items
        $streams = [];

        foreach ($this->client->streamLiveStreams() as $stream) {
            $streams[] = $stream;
        }

        // Should have collected streams without memory issues
        $this->assertGreaterThan(0, count($streams));
    }
}
```

### Example 3: Test with .env.testing

```php
<?php

namespace Tests;

class TestCase extends \PHPUnit\Framework\TestCase
{
    protected function setUp(): void
    {
        parent::setUp();

        // Load .env.testing
        if (file_exists(__DIR__ . '/../.env.testing')) {
            $dotenv = \Dotenv\Dotenv::createImmutable(
                __DIR__ . '/..',
                '.env.testing'
            );
            $dotenv->load();
        }

        // Set mock URL from environment
        $_ENV['XTREAM_BASE_URL'] = $_ENV['XTREAM_BASE_URL'] ?? 'http://localhost:3000';
    }
}
```

---

## Adding New Endpoints to Mock

### Method 1: Using Mockoon GUI (Recommended for beginners)

1. Open Mockoon desktop app
2. Select "Xtream Codes API Mock" environment
3. Click **"+"** button to add new route
4. Set:
   - **Endpoint**: `player_api.php`
   - **Method**: GET
   - **Responses**: Add response with sample JSON

### Method 2: Edit Collection File Directly

Edit `mockoon/mockoon-xtream-collection.json`:

```json
{
  "type": "route",
  "uuid": "route-new-endpoint",
  "documentation": "Your endpoint description",
  "method": "get",
  "endpoint": "player_api.php",
  "responses": [
    {
      "uuid": "response-new-success",
      "name": "Success Response",
      "statusCode": 200,
      "headers": [
        {
          "key": "Content-Type",
          "value": "application/json"
        }
      ],
      "body": "{\"result\": \"your data here\"}",
      "latency": 100,
      "isDefault": true
    }
  ]
}
```

Then reload in Mockoon or restart the mock server.

---

## Testing Different Scenarios

### Scenario 1: Success Response
```bash
curl "http://localhost:3000/player_api.php?username=testuser&password=testpass&action=get_live_categories"
```
Response: Array of categories

### Scenario 2: No Results
In Mockoon GUI, change the response to use "No Categories" response (empty array):
```bash
curl "http://localhost:3000/player_api.php?username=testuser&password=testpass&action=get_live_categories"
```
Response: `[]`

### Scenario 3: Authentication Failed
In Mockoon GUI, change response to "Auth Failed":
```bash
curl "http://localhost:3000/player_api.php?username=wronguser&password=wrongpass"
```
Response: `{"user_info": {"auth": 0}}`

---

## CI/CD Pipeline Integration

### GitHub Actions Example

```yaml
name: Test with Mockoon

on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest

    services:
      mockoon:
        image: mockoon/mockoon:latest
        options: >-
          --health-cmd "curl -f http://localhost:3000/player_api.php || exit 1"
          --health-interval 5s
          --health-timeout 3s
          --health-retries 3
          --health-start-period 10s
        ports:
          - 3000:3000
        volumes:
          - ${{ github.workspace }}/mockoon/mockoon-xtream-collection.json:/data/collection.json:ro
        env:
          MOCKOON_DATA_FILE: /data/collection.json

    steps:
      - uses: actions/checkout@v2

      - name: Set up PHP
        uses: shivammathur/setup-php@v2
        with:
          php-version: '8.1'

      - name: Install dependencies
        run: composer install

      - name: Wait for Mockoon
        run: |
          until curl http://localhost:3000/player_api.php?username=testuser&password=testpass; do
            echo 'Waiting for Mockoon...'
            sleep 1
          done

      - name: Run tests
        run: |
          export XTREAM_BASE_URL=http://localhost:3000
          ./vendor/bin/phpunit tests/Unit/Services/Xtream/
```

### GitLab CI Example

```yaml
test:with:mockoon:
  image: php:8.1
  services:
    - name: mockoon/mockoon:latest
      alias: mockoon
      variables:
        MOCKOON_DATA_FILE: /data/collection.json
  before_script:
    - cp mockoon/mockoon-xtream-collection.json /tmp/collection.json
    - apt-get update && apt-get install -y curl
    - composer install
    - |
      for i in {1..30}; do
        if curl http://mockoon:3000/player_api.php?username=testuser&password=testpass; then
          break
        fi
        sleep 1
      done
  script:
    - export XTREAM_BASE_URL=http://mockoon:3000
    - ./vendor/bin/phpunit tests/Unit/Services/Xtream/
```

---

## Troubleshooting

### Mock Server Won't Start

**Problem**: Port 3000 already in use
```bash
# Find process using port 3000
lsof -i :3000

# Kill the process
kill -9 <PID>

# Or use different port
docker run -p 3001:3000 mockoon/mockoon:latest ...
```

### Connection Refused in Tests

**Problem**: Mock URL is incorrect
```php
// Wrong - localhost won't work in Docker containers
$url = 'http://localhost:3000';

// Correct - use service name
$url = 'http://mockoon-xtream:3000';
```

### 404 Errors from Mock

**Problem**: Endpoint not defined
- Check that the route path matches exactly
- Xtream uses `player_api.php` - make sure this is the endpoint
- Check that response is set as default (`"isDefault": true`)

### Memory Issues with Large Responses

Use streaming methods:
```php
// Bad - loads entire response into memory
$streams = $this->client->getLiveStreams();

// Good - processes item by item
foreach ($this->client->streamLiveStreams() as $stream) {
    // Process one stream at a time
}
```

---

## Best Practices

1. **Keep mock data realistic** - Use actual field names and data types
2. **Version control the collection** - Commit `mockoon/mockoon-xtream-collection.json` to git
3. **Document changes** - Add comments when updating mock responses
4. **Use environment variables** - Switch between mock/real API easily
5. **Test error scenarios** - Add responses for failures, timeouts, etc.
6. **Run locally first** - Test mock locally before using in CI/CD

---

## Performance Notes

- Mock responses have configurable latency (100-150ms in current setup)
- Streaming methods handle large datasets efficiently
- Docker startup time: ~5-10 seconds
- Memory usage: ~50MB per Mockoon instance

---

## Next Steps

1. ✅ Import the collection into Mockoon GUI
2. ✅ Start mock server locally
3. ✅ Run your tests against `http://localhost:3000`
4. ✅ Set up Docker for CI/CD
5. ✅ Add more endpoints as needed
6. ✅ Update .env files to use mock for testing

---

For more information:
- Mockoon Docs: https://docs.mockoon.com
- Collection Format: https://docs.mockoon.com/latest/mockoon-data-files/
- Docker Setup: https://docs.mockoon.com/latest/docker/
