# Testing & CI/CD Guide

Complete guide for running tests locally and in GitHub Actions.

## 📋 Table of Contents

- [Quick Start](#quick-start)
- [Local Testing](#local-testing)
- [GitHub Actions](#github-actions)
- [Test Reports](#test-reports)
- [Troubleshooting](#troubleshooting)

---

## 🚀 Quick Start

### Run All Tests Locally

```bash
# Terminal 1: Backend API
cd back && php -S localhost:8000

# Terminal 2: Frontend Dev Server
cd admin && npm run dev

# Terminal 3: Mock API
./mockoon/scripts/mockoon.sh start

# Terminal 4: Run Tests
cd admin && npm test
```

### Run Tests in GitHub

Just push or create a PR to `main` or `develop` branch. Tests run automatically!

---

## 🧪 Local Testing

### Prerequisites

```bash
# Backend
cd back
composer install
php -S localhost:8000

# Frontend
cd admin
npm install
npm run dev

# Mock Server
./mockoon/scripts/mockoon.sh start
```

### Run Tests

```bash
cd admin

# All tests
npm test

# Specific test file
npm test -- tests/api/auth.spec.ts

# API tests only
npm test -- tests/api

# UI tests only
npm test -- tests/ui

# Integration tests only
npm test -- tests/integration

# Headed mode (see browser)
npm test -- --headed

# Debug mode (step through)
npm test -- --debug

# Specific browser
npm test -- --project=chromium
npm test -- --project=firefox
npm test -- --project=webkit
```

### View Test Report

```bash
# After running tests
npx playwright show-report
```

---

## 🔄 GitHub Actions

### Workflows

#### 1. Playwright Tests (`.github/workflows/playwright-tests.yml`)

**Triggers:**
- Push to `main`, `develop`, or `feature/**` branches
- Pull requests to `main` or `develop`
- Changes to `admin/` or `back/` directories

**What it does:**
1. ✅ Sets up PHP 8.3
2. ✅ Installs backend dependencies (composer)
3. ✅ Runs PHP migrations
4. ✅ Starts PHP development server (localhost:8000)
5. ✅ Sets up Node.js 20
6. ✅ Installs frontend dependencies (npm)
7. ✅ Builds React app
8. ✅ Installs Playwright browsers
9. ✅ Waits for services to be ready
10. ✅ Runs PHPUnit tests (backend)
11. ✅ Runs PHPStan static analysis
12. ✅ Runs Playwright API tests
13. ✅ Runs Playwright UI tests
14. ✅ Runs Playwright integration tests
15. ✅ Uploads test reports and artifacts
16. ✅ Publishes test results

**Duration:** ~10-15 minutes

#### 2. Test Results PR Comment (`.github/workflows/tests-pr-comment.yml`)

**Triggers:**
- When Playwright Tests workflow completes on a PR

**What it does:**
1. Downloads test artifacts
2. Parses test results
3. Posts summary comment on PR with:
   - Number of tests passed
   - Number of tests failed
   - Number of tests skipped
   - Total execution time
   - Link to full report

---

## 📊 Test Reports

### Viewing Reports

#### GitHub Actions

1. Go to **Actions** tab in GitHub
2. Click **Playwright Tests** workflow
3. Scroll down to **Artifacts**
4. Download desired artifact:
   - `playwright-report` - Full HTML report
   - `playwright-test-results-json` - JSON results
   - `playwright-test-results-junit` - JUnit XML
   - `playwright-screenshots` - Failed test screenshots
   - `playwright-videos` - Failed test videos

#### PR Comments

Test results are automatically posted as comments on your PR when:
- Tests complete successfully
- Tests fail (if configured)

Shows:
- Number passed/failed
- Execution duration
- Link to full report

#### Local

```bash
# View HTML report
npx playwright show-report

# View test results JSON
cat test-results/results.json

# View JUnit XML
cat test-results/junit.xml
```

---

## 🧬 Test Structure

### Backend Tests

**PHPUnit Unit Tests:** `back/tests/Unit/`
```bash
./vendor/bin/phpunit tests/Unit/
```

**PHPUnit Feature Tests:** `back/tests/Feature/`
```bash
./vendor/bin/phpunit tests/Feature/
```

**PHPStan Analysis:**
```bash
./vendor/bin/phpstan analyse src/
```

### Frontend Tests

**Playwright Tests:** `admin/tests/`

```
tests/
├── api/              # REST API endpoint tests
│   ├── auth.spec.ts
│   ├── sources.spec.ts
│   ├── clients.spec.ts
│   └── filters.spec.ts
├── ui/               # React component tests
│   ├── auth.spec.ts
│   ├── sources.spec.ts
│   └── dashboard.spec.ts
└── integration/      # End-to-end tests
    └── full-workflow.spec.ts
```

---

## 🔧 Configuration

### GitHub Actions Config

**File:** `.github/workflows/playwright-tests.yml`

Key environment variables:
```yaml
API_BASE_URL: http://localhost:8000
ADMIN_PANEL_URL: http://localhost:5173/admin
DB_TYPE: sqlite
DB_SQLITE_PATH: :memory:
```

### Playwright Config

**File:** `admin/playwright.config.ts`

- 5 projects: Chromium, Firefox, WebKit, Mobile Chrome, Mobile Safari
- Timeout: 30 seconds per test
- Retries: 2 on CI, 0 locally
- Workers: 1 on CI, auto locally
- Reporters: HTML, JSON, JUnit

### Services

**Services in CI:**
- PHP 8.3 backend on port 8000
- Mockoon mock server on port 3000
- Both have health checks

---

## ✅ Test Coverage

### Backend

| Layer | Tests | Type |
|-------|-------|------|
| Units | ~14 | PHPUnit |
| Feature | - | PHPUnit |
| Analysis | - | PHPStan |

### Frontend

| Layer | Tests | Type |
|-------|-------|------|
| API | 31 | Playwright |
| UI | 28 | Playwright |
| Integration | 4 | Playwright |

**Total: ~77 tests**

---

## 🐛 Debugging

### Local Debugging

```bash
# See browser while testing
npm test -- --headed

# Step through tests
npm test -- --debug

# Record session for replay
npx playwright codegen http://localhost:5173/admin

# View traces of failed tests
npx playwright show-trace trace.zip
```

### CI Debugging

1. Download artifacts from failed workflow
2. View `playwright-screenshots/` for failure screenshots
3. View `playwright-report/` for detailed report
4. Check workflow logs for error details

---

## 🚨 Troubleshooting

### Port Already in Use

```bash
# Find process on port
lsof -i :8000  # Backend
lsof -i :5173  # Frontend
lsof -i :3000  # Mockoon

# Kill process
kill -9 <PID>
```

### Tests Timeout

**Local:**
```bash
# Increase timeout
npm test -- --timeout 60000
```

**CI:**
Edit `.github/workflows/playwright-tests.yml`:
```yaml
timeout-minutes: 90  # Increase from 60
```

### Services Won't Start

**Check backend:**
```bash
curl http://localhost:8000/api/auth/login

# Check logs
tail -f /tmp/php.log
```

**Check Mockoon:**
```bash
curl http://localhost:3000/player_api.php?username=testuser&password=testpass
```

**Check frontend:**
```bash
# Should be running on 5173
curl http://localhost:5173/
```

### GitHub Actions Fails

1. Check workflow logs for error messages
2. Verify all services started correctly
3. Check environment variables
4. Verify PHP version (8.3) and Node version (20)

### Tests Fail in CI but Pass Locally

**Common causes:**
- Port conflicts
- Timing issues (use proper waits, not sleeps)
- Database state
- Environment variables not set

**Solution:**
1. Check CI logs carefully
2. Recreate CI environment locally
3. Add more detailed logging
4. Check for race conditions

---

## 📈 Performance

### Local Test Execution

- **All tests**: ~5-10 minutes
- **API tests**: ~2 minutes
- **UI tests**: ~3 minutes
- **Integration tests**: ~2 minutes

### CI Test Execution

- **Setup**: ~3 minutes
- **Install deps**: ~2 minutes
- **Backend tests**: ~1 minute
- **Playwright tests**: ~5-8 minutes
- **Upload artifacts**: ~1 minute
- **Total**: ~12-15 minutes

### Optimizations

✅ Use `npm ci` instead of `npm install` in CI
✅ Cache npm/composer dependencies
✅ Parallel test execution (when available)
✅ Don't rebuild frontend if not needed

---

## 📝 Best Practices

### Writing Tests

1. ✅ Test one thing per test
2. ✅ Use descriptive test names
3. ✅ Set up and tear down properly
4. ✅ Use proper waits, not arbitrary sleeps
5. ✅ Use test data fixtures
6. ✅ Handle errors gracefully

### CI/CD

1. ✅ Commit test files with code
2. ✅ Run tests before pushing
3. ✅ Fix failing tests immediately
4. ✅ Don't skip tests in CI
5. ✅ Monitor test trends
6. ✅ Keep workflows simple and clear

---

## 🔐 Security

### Test Data

✅ Use test user credentials (admin/admin)
✅ Don't use production data in tests
✅ Don't commit real API keys
✅ Use environment variables for secrets

### CI/CD Secrets

To use secrets in workflows:

```yaml
env:
  API_KEY: ${{ secrets.API_KEY }}
```

Configure in GitHub: Settings → Secrets and variables → Actions

---

## 📚 Useful Links

- [Playwright Documentation](https://playwright.dev)
- [GitHub Actions Documentation](https://docs.github.com/en/actions)
- [PHPUnit Documentation](https://phpunit.de)
- [PHPStan Documentation](https://phpstan.org)

---

## 🎯 Workflow Status

Check status of tests:

1. Go to **Actions** tab in GitHub
2. Click **Playwright Tests** workflow
3. See status of latest run
4. Green ✅ = All tests passed
5. Red ❌ = Some tests failed

---

## 📋 Checklist Before Pushing

- [ ] Run tests locally: `npm test`
- [ ] All tests pass
- [ ] No linting errors
- [ ] No type errors
- [ ] Screenshots and videos work
- [ ] New tests written for new features
- [ ] Commit message is clear

---

**Last Updated**: 2025-12-13
**Playwright Version**: 1.48+
**Node Version**: 20+
**PHP Version**: 8.3
