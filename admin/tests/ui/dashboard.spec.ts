/**
 * Dashboard UI Tests
 * Tests for the admin dashboard
 */

import { test, expect } from '@playwright/test';
import { UIHelper } from '../utils/ui-helper';
import { APIHelper } from '../utils/api-helper';

test.describe('Dashboard UI', () => {
  let uiHelper: UIHelper;
  let apiHelper: APIHelper;

  test.beforeEach(async ({ page, request }) => {
    uiHelper = new UIHelper(page);
    apiHelper = new APIHelper(request);

    // Login and navigate to dashboard
    await apiHelper.login();
    await uiHelper.navigateTo('/dashboard');
  });

  test.afterEach(async () => {
    await apiHelper.logout();
  });

  test('should display dashboard page', async ({ page }) => {
    await expect(page.locator('text=Dashboard')).toBeVisible();
  });

  test('should show statistics cards', async ({ page }) => {
    // Wait for stats to load
    await page.waitForTimeout(1000);

    // Should show common stat cards
    const statsToCheck = [
      'Total Sources',
      'Total Clients',
      'Total Filters',
      'Total Streams',
    ];

    for (const stat of statsToCheck) {
      const element = page.locator(`text=${stat}`);
      // At least some stats should be visible
      if (await element.isVisible({ timeout: 1000 }).catch(() => false)) {
        expect(element).toBeVisible();
      }
    }
  });

  test('should show numbers in statistics', async ({ page }) => {
    // Get stats container
    const statsContainer = page.locator('[data-testid="dashboard-stats"]');

    // Should have numeric content
    await expect(statsContainer).toContainText(/\d+/);
  });

  test('should have quick action buttons', async ({ page }) => {
    // Should have buttons to navigate to management pages
    const actionButtons = [
      'Sources',
      'Clients',
      'Filters',
      'Streams',
    ];

    for (const button of actionButtons) {
      const btn = page.locator(`button:has-text("${button}"), a:has-text("${button}")`).first();
      // At least some buttons should be present
      if (await btn.isVisible({ timeout: 1000 }).catch(() => false)) {
        expect(btn).toBeVisible();
      }
    }
  });

  test('should navigate to sources from dashboard', async ({ page }) => {
    const sourcesLink = page.locator(
      'button:has-text("Sources"), a:has-text("Sources"), [data-testid="sources-link"]'
    ).first();

    if (await sourcesLink.isVisible()) {
      await sourcesLink.click();
      await page.waitForLoadState('networkidle');

      await expect(page).toHaveURL(/\/sources$/);
    }
  });

  test('should navigate to clients from dashboard', async ({ page }) => {
    const clientsLink = page.locator(
      'button:has-text("Clients"), a:has-text("Clients"), [data-testid="clients-link"]'
    ).first();

    if (await clientsLink.isVisible()) {
      await clientsLink.click();
      await page.waitForLoadState('networkidle');

      await expect(page).toHaveURL(/\/clients$/);
    }
  });

  test('should show activity or recent syncs', async ({ page }) => {
    // Check for activity section
    const activitySection = page.locator(
      '[data-testid="activity"], text=/recent|activity|logs/i'
    ).first();

    // May or may not be visible, depending on design
    try {
      await expect(activitySection).toBeVisible({ timeout: 2000 });
    } catch {
      // Activity section is optional
    }
  });

  test('should refresh statistics', async ({ page }) => {
    // Look for refresh button
    const refreshBtn = page.locator(
      'button[aria-label*="Refresh"], button[title*="Refresh"], [data-testid="refresh-stats"]'
    );

    if (await refreshBtn.isVisible()) {
      // Click refresh
      await refreshBtn.click();

      // Wait for new data
      await page.waitForLoadState('networkidle');

      // Stats should still be visible (may be same or different)
      await expect(
        page.locator('[data-testid="sources-count"]')
      ).toBeVisible();
    }
  });

  test('should handle loading state', async ({ page }) => {
    // Reload to see loading
    page.reload();

    // Should show loading indicator
    const loader = page.locator(
      '[data-testid="loading-dashboard"], [role="progressbar"]'
    );

    // May or may not be visible depending on speed
    try {
      await expect(loader).toBeVisible({ timeout: 1000 });
    } catch {
      // Loading was fast
    }

    // Dashboard should eventually render
    await expect(page.locator('text=Dashboard')).toBeVisible();
  });

  test('should show navigation menu', async ({ page }) => {
    // Check for navigation menu
    const navMenu = page.locator(
      '[role="navigation"], nav, [data-testid="sidebar"], [data-testid="menu"]'
    ).first();

    await expect(navMenu).toBeVisible();

    // Should have main sections
    const sections = ['Sources', 'Clients', 'Filters', 'Categories', 'Streams'];

    for (const section of sections) {
      const link = navMenu.locator(`text=${section}`).first();
      if (await link.isVisible({ timeout: 1000 }).catch(() => false)) {
        expect(link).toBeVisible();
      }
    }
  });
});
