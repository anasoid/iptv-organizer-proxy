# Playwright Testing Guide

Complete guide for running API and UI tests using Playwright.

## 📋 Table of Contents

- [Quick Start](#quick-start)
- [Project Structure](#project-structure)
- [Setup](#setup)
- [Running Tests](#running-tests)
- [Writing Tests](#writing-tests)
- [Debugging](#debugging)
- [CI/CD Integration](#cicd-integration)
- [Best Practices](#best-practices)
- [Troubleshooting](#troubleshooting)

---

## 🚀 Quick Start

### Prerequisites

- Node.js 20+
- PHP 8.2+ (for backend API)
- Docker (optional, for Mockoon)

### 5-Minute Setup

```bash
# 1. Install dependencies
cd admin
npm install

# 2. Install Playwright browsers
npx playwright install

# 3. Start backend API (in another terminal)
cd back
php -S localhost:8000

# 4. Start frontend dev server (in another terminal)
cd admin
npm run dev

# 5. Start Mockoon mock server (in another terminal)
./mockoon/scripts/mockoon.sh start

# 6. Run tests
npm test
```

---

## 📁 Project Structure

```
admin/
├── playwright.config.ts        # Playwright configuration
├── tests/
│   ├── api/                    # API endpoint tests
│   │   ├── auth.spec.ts        # Authentication tests
│   │   ├── sources.spec.ts     # Sources CRUD tests
│   │   ├── clients.spec.ts     # Clients CRUD tests
│   │   └── filters.spec.ts     # Filters CRUD tests
│   │
│   ├── ui/                     # UI/React component tests
│   │   ├── auth.spec.ts        # Login/logout tests
│   │   ├── sources.spec.ts     # Sources management tests
│   │   └── dashboard.spec.ts   # Dashboard tests
│   │
│   ├── integration/            # End-to-end workflow tests
│   │   └── full-workflow.spec.ts  # Complete user workflows
│   │
│   ├── fixtures/               # Test data and setup
│   │   ├── test-data.ts        # Test constants and fixtures
│   │   └── base-fixtures.ts    # Custom test fixtures
│   │
│   └── utils/                  # Helper utilities
│       ├── api-helper.ts       # API request helpers
│       └── ui-helper.ts        # UI interaction helpers
│
├── test-results/               # Generated test results
├── playwright-report/          # Generated HTML report
└── screenshots/                # Failed test screenshots
```

---

## 🔧 Setup

### 1. Install Dependencies

```bash
cd admin
npm install
npx playwright install
```

### 2. Configure Environment

Create `.env.test` in the admin folder (optional):

```ini
API_BASE_URL=http://localhost:8000
ADMIN_PANEL_URL=http://localhost:5173/admin
```

### 3. Start Services

#### Backend API

```bash
cd back
php -S localhost:8000
```

#### Frontend Dev Server

```bash
cd admin
npm run dev
```

#### Mockoon Mock Server

```bash
./mockoon/scripts/mockoon.sh start
```

Or use Docker:

```bash
docker-compose -f mockoon/docker-compose.yml up -d mockoon-xtream
```

---

## 🧪 Running Tests

### Run All Tests

```bash
npm test
```

### Run Specific Test File

```bash
# API tests
npm test -- tests/api/sources.spec.ts

# UI tests
npm test -- tests/ui/auth.spec.ts

# Integration tests
npm test -- tests/integration/full-workflow.spec.ts
```

### Run Tests by Pattern

```bash
# All source-related tests
npm test -- --grep "Sources"

# All authentication tests
npm test -- --grep "Auth"

# All API tests
npm test -- tests/api
```

### Run Tests in Headed Mode (See Browser)

```bash
npm test -- --headed
```

### Run Tests in Specific Browser

```bash
# Chromium only
npm test -- --project=chromium

# Firefox only
npm test -- --project=firefox

# Safari only
npm test -- --project=webkit

# All browsers in parallel
npm test -- --project=chromium --project=firefox --project=webkit
```

### Debug Mode

```bash
# Pause and inspect tests
npm test -- --debug

# Or use Playwright Inspector
npx playwright test --debug
```

### Generate HTML Report

```bash
npm test
npx playwright show-report
```

---

## ✍️ Writing Tests

### Basic API Test

```typescript
import { test, expect } from '@playwright/test';
import { APIHelper } from '../utils/api-helper';

test('should get all sources', async ({ request }) => {
  const apiHelper = new APIHelper(request);
  await apiHelper.login();

  const response = await apiHelper.getSources();

  expect(response.success).toBeTruthy();
  expect(Array.isArray(response.data)).toBeTruthy();
});
```

### Basic UI Test

```typescript
import { test, expect } from '@playwright/test';
import { UIHelper } from '../utils/ui-helper';

test('should login successfully', async ({ page }) => {
  const uiHelper = new UIHelper(page);

  await uiHelper.login('admin', 'admin');

  await expect(page).toHaveURL('http://localhost:5173/admin/dashboard');
});
```

### Using Custom Fixtures

```typescript
import { test, expect } from '../fixtures/base-fixtures';

test('should work with both API and UI', async ({ page, request, apiHelper, uiHelper }) => {
  // Login via UI
  await uiHelper.login();

  // Create data via API
  const source = await apiHelper.createSource({
    name: 'Test',
    url: 'http://localhost:3000',
    username: 'user',
    password: 'pass',
  });

  // Verify in UI
  await uiHelper.navigateTo('/sources');
  await expect(page.locator(`text=${source.data.name}`)).toBeVisible();
});
```

### Using Test Data Fixtures

```typescript
import { test, expect } from '@playwright/test';
import { APIHelper } from '../utils/api-helper';
import { TEST_SOURCE, TEST_CLIENT } from '../fixtures/test-data';

test('should create source and client', async ({ request }) => {
  const apiHelper = new APIHelper(request);
  await apiHelper.login();

  const source = await apiHelper.createSource(TEST_SOURCE);
  const client = await apiHelper.createClient({
    ...TEST_CLIENT,
    sourceId: source.data.id,
  });

  expect(source.data.id).toBeDefined();
  expect(client.data.id).toBeDefined();
});
```

### Organizing Tests

```typescript
test.describe('Sources Management', () => {
  test.beforeEach(async ({ page }) => {
    // Setup before each test
  });

  test.afterEach(async ({ page }) => {
    // Cleanup after each test
  });

  test.describe('Create Source', () => {
    test('should create with valid data', async () => {
      // Test implementation
    });

    test('should fail with invalid data', async () => {
      // Test implementation
    });
  });

  test.describe('Update Source', () => {
    test('should update name', async () => {
      // Test implementation
    });
  });
});
```

---

## 🐛 Debugging

### Visual Debugging

```bash
# Run with headed browser to see what's happening
npm test -- --headed
```

### Debug Mode

```bash
# Pause execution and step through
npm test -- --debug
```

### Inspector

```bash
# Open Playwright Inspector
npx playwright test --debug
```

### Screenshots on Failure

Screenshots are automatically captured for failed tests in `screenshots/`

### Videos on Failure

Videos are recorded for failed tests in the test-results directory

### Trace Viewer

```bash
# View trace of failed test
npx playwright show-trace tests/screenshots/trace.zip
```

### Console Logs

```typescript
test('should log debug info', async ({ page }) => {
  page.on('console', msg => console.log('PAGE LOG:', msg.text()));

  await page.goto('http://localhost:5173/admin');
  // Page console logs are now printed
});
```

---

## 🔄 CI/CD Integration

### GitHub Actions

Tests run automatically on:
- Push to `main` or `develop`
- Pull requests to `main` or `develop`

View in: `.github/workflows/playwright-tests.yml`

### Manual Trigger

```bash
# In GitHub Actions, click "Run workflow"
```

### View Results

1. Go to **Actions** tab in GitHub
2. Click on the latest **Playwright Tests** workflow
3. Scroll down to **Artifacts**
4. Download `playwright-report` to view HTML report

---

## ✅ Best Practices

### 1. Use Page Object Model

```typescript
// Create helper classes for page interactions
class SourcesPage {
  constructor(private page: Page) {}

  async navigateTo() {
    await this.page.goto('http://localhost:5173/admin/sources');
  }

  async clickAddButton() {
    await this.page.click('button:has-text("Add Source")');
  }

  async fillSourceForm(data: SourceData) {
    // Fill form fields
  }
}

// Use in tests
test('should create source', async ({ page }) => {
  const sourcesPage = new SourcesPage(page);
  await sourcesPage.navigateTo();
  await sourcesPage.clickAddButton();
  // ...
});
```

### 2. Test One Thing Per Test

```typescript
// Good: Single assertion per test concept
test('should create source', async () => {
  const response = await apiHelper.createSource(TEST_SOURCE);
  expect(response.success).toBeTruthy();
});

// Avoid: Multiple unrelated assertions
test('should do everything', async () => {
  // Create source
  // Update source
  // Delete source
  // Check API
  // Check UI
  // ... too much!
});
```

### 3. Use Test Fixtures for Setup

```typescript
// Good: Use fixtures for common setup
test.beforeEach(async ({ request }) => {
  apiHelper = new APIHelper(request);
  await apiHelper.login();
});

test.afterEach(async () => {
  await apiHelper.logout();
});

// Bad: Repeating setup in every test
test('test 1', async () => {
  const apiHelper = new APIHelper(request);
  await apiHelper.login();
  // test code
  await apiHelper.logout();
});
```

### 4. Wait for Elements Correctly

```typescript
// Good: Use proper waits
await page.waitForSelector('[role="dialog"]', { timeout: 5000 });
await page.waitForURL('**/sources');
await page.waitForLoadState('networkidle');

// Avoid: Arbitrary delays
await page.waitForTimeout(2000); // ❌ Flaky!
```

### 5. Use Data-TestID Attributes

```typescript
// In React component
<button data-testid="logout-button">Logout</button>

// In test
await page.click('[data-testid="logout-button"]');
```

### 6. Clean Up Data

```typescript
test.beforeEach(async () => {
  // Create test data
});

test.afterEach(async () => {
  // Delete test data created in beforeEach
  await apiHelper.deleteSource(sourceId);
});
```

---

## 🆘 Troubleshooting

### Port Already in Use

```bash
# Find process using port
lsof -i :8000  # Backend
lsof -i :5173  # Frontend
lsof -i :3000  # Mockoon

# Kill process
kill -9 <PID>
```

### Tests Timeout

**Problem**: Tests fail with timeout errors

**Solution**:
```typescript
// Increase timeout for specific test
test('slow operation', async ({ page }) => {
  // ...
}, { timeout: 30000 }); // 30 seconds

// Or in config
timeout: 30000,
```

### API Returns 401

**Problem**: Tests get 401 Unauthorized

**Solution**:
```typescript
// Make sure to login first
await apiHelper.login();

// Verify token is set
const headers = apiHelper.getAuthHeaders();
console.log('Headers:', headers);
```

### Browser Won't Start

**Problem**: Playwright can't launch browser

**Solution**:
```bash
# Reinstall browsers
npx playwright install

# Run with verbose logging
DEBUG=pw:api npm test
```

### Tests Fail Intermittently

**Problem**: Tests pass sometimes, fail others (flaky)

**Solution**:
```typescript
// Use proper waits, not timeouts
// ✅ Good
await page.waitForSelector('text=Success', { timeout: 5000 });

// ❌ Bad
await page.waitForTimeout(1000);
await expect(page.locator('text=Success')).toBeVisible();
```

### API Tests Can't Connect

**Problem**: API tests fail to connect to backend

**Solution**:
```bash
# 1. Verify backend is running
curl http://localhost:8000/api/auth/login

# 2. Check DB is initialized
cd back && php -r "require 'vendor/autoload.php'; require 'src/Database/Migrations.php';"

# 3. Check base URL in test
API_BASE_URL=http://localhost:8000 npm test
```

### Screenshots Are Blurry

**Problem**: Failure screenshots are hard to read

**Solution**:
```typescript
// Configure higher resolution
use: {
  viewport: { width: 1920, height: 1080 },
},
```

---

## 📊 Test Coverage

### Current Test Coverage

| Layer | Tests | Coverage |
|-------|-------|----------|
| **Authentication** | 8 | Login, logout, validation |
| **Sources API** | 15 | CRUD, sync, test connection |
| **Sources UI** | 10 | List, create, edit, delete |
| **Clients API** | 8 | CRUD operations |
| **Filters API** | 8 | CRUD, validation |
| **Dashboard** | 8 | Stats, navigation |
| **Integration** | 4 | Full workflows |
| **Total** | **61** | Comprehensive coverage |

### Adding Tests

To add more tests:

1. Create new file: `admin/tests/api/new-endpoint.spec.ts`
2. Import helpers: `import { test, expect } from '@playwright/test';`
3. Write tests following patterns above
4. Run: `npm test -- tests/api/new-endpoint.spec.ts`

---

## 📚 Useful Links

- [Playwright Documentation](https://playwright.dev)
- [Playwright Test Configuration](https://playwright.dev/docs/test-configuration)
- [Playwright Assertions](https://playwright.dev/docs/test-assertions)
- [Playwright API](https://playwright.dev/docs/api/class-apirequestcontext)

---

## 🤝 Contributing

When adding new features:

1. ✅ Add API tests first
2. ✅ Add UI tests
3. ✅ Add integration tests
4. ✅ Run full test suite: `npm test`
5. ✅ Check coverage: `npx playwright show-report`
6. ✅ Update this documentation

---

**Last Updated**: 2025-12-13
**Playwright Version**: 1.48+
**Node Version**: 20+
