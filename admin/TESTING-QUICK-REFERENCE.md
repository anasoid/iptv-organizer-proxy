# Playwright Testing - Quick Reference

Cheat sheet for common testing tasks.

## 🚀 Installation & Setup

```bash
# Install dependencies
npm install

# Install Playwright browsers
npx playwright install
```

## ▶️ Running Tests

```bash
# Run all tests
npm test

# Run specific file
npm test -- tests/api/sources.spec.ts

# Run tests matching pattern
npm test -- --grep "Sources"

# Run in headed mode (see browser)
npm test -- --headed

# Run specific browser
npm test -- --project=chromium

# Debug mode (pause and step through)
npm test -- --debug
```

## 📊 Viewing Results

```bash
# Show HTML report
npx playwright show-report

# Show test traces
npx playwright show-trace <trace-file>
```

## ✍️ Test Structure

### File Organization

```
tests/
├── api/              # API endpoint tests
├── ui/               # UI/React component tests
└── integration/      # End-to-end workflows
```

### Basic Test Template

```typescript
import { test, expect } from '@playwright/test';

test('should do something', async ({ page }) => {
  // Arrange
  await page.goto('http://localhost:5173/admin');

  // Act
  await page.fill('input[name="username"]', 'admin');
  await page.click('button[type="submit"]');

  // Assert
  await expect(page).toHaveURL('**/dashboard');
});
```

## 🛠️ Using Helpers

### API Helper

```typescript
import { APIHelper } from '../utils/api-helper';

const apiHelper = new APIHelper(request);

// Login
await apiHelper.login('admin', 'admin');

// Create
const source = await apiHelper.createSource({
  name: 'Test',
  url: 'http://localhost:3000',
  username: 'user',
  password: 'pass',
});

// Read
const sources = await apiHelper.getSources();
const source = await apiHelper.getSource(1);

// Update
await apiHelper.updateSource(1, { name: 'Updated' });

// Delete
await apiHelper.deleteSource(1);

// Logout
await apiHelper.logout();
```

### UI Helper

```typescript
import { UIHelper } from '../utils/ui-helper';

const uiHelper = new UIHelper(page);

// Navigation
await uiHelper.navigateTo('/sources');
await uiHelper.clickNavLink('Sources');

// Forms
await uiHelper.fillField('Name', 'Test Source');
await uiHelper.fillTextarea('Config', 'some: yaml');
await uiHelper.selectDropdown('Source', 'Test Source');
await uiHelper.submitForm();

// Tables
await uiHelper.waitForTable();
const row = await uiHelper.getTableRowByText('Test Source');
await uiHelper.clickRowAction('Test Source', 'Edit');

// Auth
await uiHelper.login('admin', 'admin');
await uiHelper.logout();

// Messages
await uiHelper.expectSuccessMessage('Created successfully');
await uiHelper.expectErrorMessage('Error occurred');
```

### Test Data

```typescript
import {
  TEST_ADMIN_USER,
  TEST_SOURCE,
  TEST_CLIENT,
  TEST_FILTER,
} from '../fixtures/test-data';

// Use test data
const source = await apiHelper.createSource(TEST_SOURCE);
const client = await apiHelper.createClient({
  ...TEST_CLIENT,
  sourceId: source.data.id,
});
```

## 🧪 API Test Examples

### List Endpoint

```typescript
test('should list sources', async ({ request }) => {
  const apiHelper = new APIHelper(request);
  await apiHelper.login();

  const response = await apiHelper.getSources();

  expect(response.success).toBeTruthy();
  expect(Array.isArray(response.data)).toBeTruthy();
});
```

### Create Endpoint

```typescript
test('should create source', async ({ request }) => {
  const apiHelper = new APIHelper(request);
  await apiHelper.login();

  const response = await apiHelper.createSource({
    name: 'Test',
    url: 'http://localhost:3000',
    username: 'user',
    password: 'pass',
  });

  expect(response.success).toBeTruthy();
  expect(response.data.id).toBeDefined();
});
```

### Update Endpoint

```typescript
test('should update source', async ({ request }) => {
  const apiHelper = new APIHelper(request);
  await apiHelper.login();

  const created = await apiHelper.createSource(TEST_SOURCE);
  const response = await apiHelper.updateSource(created.data.id, {
    name: 'Updated Name',
  });

  expect(response.data.name).toBe('Updated Name');
});
```

### Delete Endpoint

```typescript
test('should delete source', async ({ request }) => {
  const apiHelper = new APIHelper(request);
  await apiHelper.login();

  const created = await apiHelper.createSource(TEST_SOURCE);
  const response = await apiHelper.deleteSource(created.data.id);

  expect(response.success).toBeTruthy();
});
```

## 🖥️ UI Test Examples

### Login

```typescript
test('should login', async ({ page }) => {
  const uiHelper = new UIHelper(page);
  await uiHelper.login('admin', 'admin');

  await expect(page).toHaveURL('**/dashboard');
});
```

### Create Item

```typescript
test('should create source', async ({ page }) => {
  const uiHelper = new UIHelper(page);
  await uiHelper.login();
  await uiHelper.navigateTo('/sources');

  await page.click('button:has-text("Add Source")');
  await page.waitForSelector('[role="dialog"]');

  await uiHelper.fillField('Name', 'Test Source');
  await uiHelper.fillField('URL', 'http://localhost:3000');
  await uiHelper.fillField('Username', 'user');
  await uiHelper.fillField('Password', 'pass');

  await uiHelper.submitForm();
  await uiHelper.expectSuccessMessage('Created');
});
```

### Edit Item

```typescript
test('should edit source', async ({ page }) => {
  const uiHelper = new UIHelper(page);
  await uiHelper.login();
  await uiHelper.navigateTo('/sources');

  const row = await uiHelper.getTableRowByText('Test Source');
  await uiHelper.clickRowAction('Test Source', 'Edit');

  await page.fill('input[aria-label*="Name"]', 'Updated Name');
  await uiHelper.submitForm();

  await uiHelper.expectSuccessMessage('Updated');
});
```

### Delete Item

```typescript
test('should delete source', async ({ page }) => {
  const uiHelper = new UIHelper(page);
  await uiHelper.login();
  await uiHelper.navigateTo('/sources');

  await uiHelper.handleAlert('confirm');
  await uiHelper.clickRowAction('Test Source', 'Delete');

  await uiHelper.expectSuccessMessage('Deleted');
});
```

## 🧬 Selectors

```typescript
// By text
page.locator('text=Login');
page.locator('button:has-text("Submit")');

// By role
page.locator('[role="button"]');
page.locator('[role="grid"]');
page.locator('[role="dialog"]');

// By test ID (recommended)
page.locator('[data-testid="logout-button"]');

// By label
page.locator('input[aria-label="Email"]');

// By placeholder
page.locator('input[placeholder="Enter name"]');

// By name
page.locator('input[name="username"]');

// Combined
page.locator('button:has-text("Edit"):visible');
```

## ⏱️ Waits

```typescript
// Wait for selector
await page.waitForSelector('[role="dialog"]', { timeout: 5000 });

// Wait for text
await page.waitForFunction(() =>
  document.body.textContent.includes('Success')
);

// Wait for URL
await page.waitForURL('**/sources');

// Wait for load state
await page.waitForLoadState('networkidle');
await page.waitForLoadState('domcontentloaded');

// Wait for element to be visible
await expect(page.locator('text=Success')).toBeVisible();
```

## ✅ Assertions

```typescript
// Text content
await expect(page.locator('h1')).toContainText('Dashboard');

// Visibility
await expect(page.locator('[data-testid="loading"]')).toBeHidden();
await expect(page.locator('[data-testid="error"]')).toBeVisible();

// URL
await expect(page).toHaveURL('**/sources');

// Count
await expect(page.locator('[role="row"]')).toHaveCount(5);

// Attribute
await expect(page.locator('button')).toHaveAttribute('aria-label', 'Edit');

// Class
await expect(page.locator('.active')).toHaveClass(/active/);

// Value
await expect(page.locator('input[name="email"]')).toHaveValue('test@example.com');

// Enabled/Disabled
await expect(page.locator('button')).toBeEnabled();
await expect(page.locator('[aria-disabled="true"]')).toBeDisabled();
```

## 🐛 Debugging

```typescript
// Pause execution
await page.pause();

// Console logs
page.on('console', msg => console.log('LOG:', msg.text()));

// Network logs
page.on('response', response =>
  console.log(response.status(), response.url())
);

// Screenshot
await page.screenshot({ path: 'debug.png' });

// HTML snapshot
await page.content();

// Video
// Enabled in config
```

## 📦 Service Setup/Teardown

```typescript
test.beforeEach(async ({ request, page }) => {
  // Run before each test
  apiHelper = new APIHelper(request);
  await apiHelper.login();
});

test.afterEach(async () => {
  // Run after each test
  await apiHelper.logout();
});

test.beforeAll(async () => {
  // Run once before all tests
});

test.afterAll(async () => {
  // Run once after all tests
});
```

## 📋 Common Test Patterns

### Create and Verify

```typescript
test('create and verify', async ({ request, page }) => {
  const apiHelper = new APIHelper(request);
  const uiHelper = new UIHelper(page);

  await apiHelper.login();

  // Create via API
  const source = await apiHelper.createSource(TEST_SOURCE);

  // Verify in UI
  await uiHelper.navigateTo('/sources');
  await expect(page.locator(`text=${source.data.name}`)).toBeVisible();
});
```

### List and Filter

```typescript
test('filter list', async ({ page }) => {
  const uiHelper = new UIHelper(page);
  await uiHelper.login();
  await uiHelper.navigateTo('/sources');

  const search = page.locator('input[placeholder*="Search"]');
  await search.fill('Test');

  await page.waitForTimeout(500);
  await expect(page.locator('text=Test Source')).toBeVisible();
});
```

### Test Error Handling

```typescript
test('show error on invalid input', async ({ page }) => {
  const uiHelper = new UIHelper(page);
  await uiHelper.login();
  await uiHelper.navigateTo('/sources');

  await page.click('button:has-text("Add")');
  await page.click('button[type="submit"]');

  await expect(page.locator('text=/required|invalid/i')).toBeVisible();
});
```

## 🔍 Environment Variables

```bash
# Set in .env or command line
API_BASE_URL=http://localhost:8000
ADMIN_PANEL_URL=http://localhost:5173/admin

# Use in tests
process.env.API_BASE_URL  // 'http://localhost:8000'
```

## 📝 Performance Tips

```typescript
// ✅ Fast
test('is fast', async ({ request }) => {
  // Use API to create test data
  // Set up directly without UI clicks
  const apiHelper = new APIHelper(request);
});

// ❌ Slow
test('is slow', async ({ page }) => {
  // Click through entire UI to create test data
  // Navigate through multiple pages
  // Use arbitrary waits
});
```

## 🚨 Common Issues

### Test Fails Locally but Passes in CI

- Check Node version: `node -v`
- Clear cache: `npm ci && npx playwright install`
- Check environment variables
- Run in headed mode: `npm test -- --headed`

### Port Already in Use

```bash
lsof -i :8000 && kill -9 <PID>
```

### API 401 Unauthorized

```typescript
// Make sure login is called first
await apiHelper.login();
```

### Timeout Errors

```typescript
// Increase timeout for slow operations
test('slow test', async ({ page }) => {
  // ...
}, { timeout: 30000 });
```

---

**Quick Links**
- [Full Documentation](./TESTING.md)
- [Playwright Docs](https://playwright.dev)
- [Test Configuration](../playwright.config.ts)
