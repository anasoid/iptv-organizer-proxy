/**
 * Authentication UI Tests
 * Tests for login and logout functionality
 */

import { test, expect } from '@playwright/test';
import { UIHelper } from '../utils/ui-helper';
import { TEST_ADMIN_USER, ADMIN_PANEL_URL } from '../fixtures/test-data';

test.describe('Authentication UI', () => {
  test('should display login page', async ({ page }) => {
    await page.goto(`${ADMIN_PANEL_URL}/login`);

    await expect(page.locator('text=Login')).toBeVisible();
    await expect(page.locator('input[name="username"]')).toBeVisible();
    await expect(page.locator('input[name="password"]')).toBeVisible();
  });

  test('should login with valid credentials', async ({ page }) => {
    const uiHelper = new UIHelper(page);

    await uiHelper.login(TEST_ADMIN_USER.username, TEST_ADMIN_USER.password);

    // Should be on dashboard
    await expect(page).toHaveURL(`${ADMIN_PANEL_URL}/dashboard`);
    await expect(page.locator('text=Dashboard')).toBeVisible();
  });

  test('should show error on invalid login', async ({ page }) => {
    await page.goto(`${ADMIN_PANEL_URL}/login`);

    await page.fill('input[name="username"]', 'invalid');
    await page.fill('input[name="password"]', 'wrong');
    await page.click('button[type="submit"]');

    // Should see error message
    await expect(
      page.locator('text=/invalid credentials|login failed/i')
    ).toBeVisible({ timeout: 5000 });

    // Should still be on login page
    await expect(page).toHaveURL(`${ADMIN_PANEL_URL}/login`);
  });

  test('should require username', async ({ page }) => {
    await page.goto(`${ADMIN_PANEL_URL}/login`);

    // Leave username empty
    await page.fill('input[name="password"]', 'password');

    const submitBtn = page.locator('button[type="submit"]');
    const isDisabled = await submitBtn.isDisabled();

    // Either disabled or shows validation error
    if (!isDisabled) {
      await submitBtn.click();
      await expect(
        page.locator('text=/required|please enter/i')
      ).toBeVisible({ timeout: 5000 });
    }
  });

  test('should require password', async ({ page }) => {
    await page.goto(`${ADMIN_PANEL_URL}/login`);

    // Leave password empty
    await page.fill('input[name="username"]', TEST_ADMIN_USER.username);

    const submitBtn = page.locator('button[type="submit"]');
    const isDisabled = await submitBtn.isDisabled();

    // Either disabled or shows validation error
    if (!isDisabled) {
      await submitBtn.click();
      await expect(
        page.locator('text=/required|please enter/i')
      ).toBeVisible({ timeout: 5000 });
    }
  });

  test('should logout successfully', async ({ page }) => {
    const uiHelper = new UIHelper(page);

    // Login first
    await uiHelper.login();

    // Click logout
    await page.click('[data-testid="logout-button"]');

    // Should be redirected to login
    await expect(page).toHaveURL(`${ADMIN_PANEL_URL}/login`);
  });

  test('should show loading state during login', async ({ page }) => {
    await page.goto(`${ADMIN_PANEL_URL}/login`);

    await page.fill('input[name="username"]', TEST_ADMIN_USER.username);
    await page.fill('input[name="password"]', TEST_ADMIN_USER.password);

    const submitBtn = page.locator('button[type="submit"]');

    // Click submit
    await submitBtn.click();

    // Should show loading indicator
    const loadingIndicator = page.locator(
      '[data-testid="login-loading"], button:has-text("Loading..."), button:disabled'
    );

    await expect(loadingIndicator).toBeVisible({ timeout: 2000 });
  });

  test('should redirect to login if not authenticated', async ({ page }) => {
    // Try to access dashboard without logging in
    await page.goto(`${ADMIN_PANEL_URL}/dashboard`);

    // Should redirect to login
    await expect(page).toHaveURL(`${ADMIN_PANEL_URL}/login`, { timeout: 5000 });
  });

  test('should prevent going back to login after logout', async ({ page }) => {
    const uiHelper = new UIHelper(page);

    // Login
    await uiHelper.login();

    // Go back button or history
    await page.goBack();

    // Should not be able to access protected page
    await expect(page).toHaveURL(`${ADMIN_PANEL_URL}/login`);
  });
});
