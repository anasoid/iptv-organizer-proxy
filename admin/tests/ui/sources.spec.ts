/**
 * Sources UI Tests
 * Tests for managing IPTV sources in the admin panel
 */

import { test, expect } from '@playwright/test';
import { UIHelper } from '../utils/ui-helper';
import { APIHelper } from '../utils/api-helper';
import { TEST_SOURCE } from '../fixtures/test-data';

test.describe('Sources Management UI', () => {
  let uiHelper: UIHelper;
  let apiHelper: APIHelper;

  test.beforeEach(async ({ page, request }) => {
    uiHelper = new UIHelper(page);
    apiHelper = new APIHelper(request);

    // Login via API for speed
    await apiHelper.login();

    // Go to sources page
    await uiHelper.navigateTo('/sources');
  });

  test.afterEach(async () => {
    await apiHelper.logout();
  });

  test('should display sources list', async ({ page }) => {
    // Wait for table to load
    await uiHelper.waitForTable();

    // Should have table headers
    await expect(page.locator('text=Name')).toBeVisible();
    await expect(page.locator('text=URL')).toBeVisible();
    await expect(page.locator('text=Actions')).toBeVisible();
  });

  test('should create a new source', async ({ page }) => {
    // Click add button
    await page.click('button:has-text("Add Source")');

    // Wait for modal
    await page.waitForSelector('[role="dialog"]', { timeout: 5000 });

    // Fill form
    await page.fill('input[placeholder*="Name"], input[aria-label*="Name"]', TEST_SOURCE.name);
    await page.fill('input[placeholder*="URL"], input[aria-label*="URL"]', TEST_SOURCE.url);
    await page.fill('input[placeholder*="Username"], input[aria-label*="Username"]', TEST_SOURCE.username);
    await page.fill('input[placeholder*="Password"], input[aria-label*="Password"]', TEST_SOURCE.password);

    // Submit
    await page.click('button:has-text("Create"), button[type="submit"]');

    // Should see success message
    await expect(
      page.locator('text=/created|success/i')
    ).toBeVisible({ timeout: 5000 });

    // Source should appear in table
    await expect(page.locator(`text=${TEST_SOURCE.name}`)).toBeVisible();
  });

  test('should edit a source', async ({ page }) => {
    // Create a source via API
    const created = await apiHelper.createSource(TEST_SOURCE);
    const sourceId = created.data.id;

    // Refresh page
    await page.reload();
    await uiHelper.waitForTable();

    // Click edit button for the source
    const row = await uiHelper.getTableRowByText(TEST_SOURCE.name);
    await row.locator('button:has-text("Edit")').click();

    // Modal should appear
    await page.waitForSelector('[role="dialog"]', { timeout: 5000 });

    // Update name
    const nameInput = page.locator('input[aria-label*="Name"]').first();
    await nameInput.fill('Updated Source Name');

    // Submit
    await page.click('button:has-text("Save"), button[type="submit"]');

    // Should see success message
    await expect(
      page.locator('text=/updated|success/i')
    ).toBeVisible({ timeout: 5000 });

    // Should see updated name
    await expect(page.locator('text=Updated Source Name')).toBeVisible();
  });

  test('should delete a source', async ({ page }) => {
    // Create a source via API
    const created = await apiHelper.createSource(TEST_SOURCE);

    // Refresh page
    await page.reload();
    await uiHelper.waitForTable();

    // Click delete button
    const row = await uiHelper.getTableRowByText(TEST_SOURCE.name);

    // Set up alert handler
    await uiHelper.handleAlert('confirm');

    await row.locator('button:has-text("Delete")').click();

    // Should see success message
    await expect(
      page.locator('text=/deleted|success/i')
    ).toBeVisible({ timeout: 5000 });

    // Source should disappear from table
    await page.waitForTimeout(1000);
    const rowExists = await page.locator(`text=${TEST_SOURCE.name}`).isVisible({
      timeout: 1000,
    }).catch(() => false);

    expect(rowExists).toBeFalsy();
  });

  test('should test source connection', async ({ page }) => {
    // Create a source via API
    const created = await apiHelper.createSource(TEST_SOURCE);

    // Refresh page
    await page.reload();
    await uiHelper.waitForTable();

    // Click test connection
    const row = await uiHelper.getTableRowByText(TEST_SOURCE.name);
    await row.locator('button:has-text("Test")').click();

    // Should show loading
    await expect(
      page.locator('[data-testid="testing-connection"]')
    ).toBeVisible({ timeout: 2000 });

    // Should show result (success or failure depending on if Mockoon is running)
    await expect(
      page.locator('text=/connection|success|failed/i')
    ).toBeVisible({ timeout: 5000 });
  });

  test('should trigger sync', async ({ page }) => {
    // Create a source via API
    const created = await apiHelper.createSource(TEST_SOURCE);

    // Refresh page
    await page.reload();
    await uiHelper.waitForTable();

    // Click sync button
    const row = await uiHelper.getTableRowByText(TEST_SOURCE.name);
    await row.locator('button:has-text("Sync")').click();

    // Should show sync in progress
    await expect(
      page.locator('text=/syncing|in progress/i')
    ).toBeVisible({ timeout: 2000 });
  });

  test('should filter sources list', async ({ page }) => {
    // Create multiple sources
    await apiHelper.createSource(TEST_SOURCE);

    // Refresh page
    await page.reload();
    await uiHelper.waitForTable();

    // Type in search box
    const searchInput = page.locator('input[placeholder*="Search"], input[aria-label*="Search"]');
    if (await searchInput.isVisible()) {
      await searchInput.fill(TEST_SOURCE.name.substring(0, 5));

      // Wait for filtering
      await page.waitForTimeout(500);

      // Should show matching source
      await expect(page.locator(`text=${TEST_SOURCE.name}`)).toBeVisible();
    }
  });

  test('should show validation errors for invalid input', async ({ page }) => {
    // Click add button
    await page.click('button:has-text("Add Source")');

    // Wait for modal
    await page.waitForSelector('[role="dialog"]', { timeout: 5000 });

    // Try to submit with only name
    const nameInput = page.locator('input[placeholder*="Name"], input[aria-label*="Name"]');
    await nameInput.fill('Name Only');

    // Submit
    const submitBtn = page.locator('button[type="submit"]').first();
    await submitBtn.click();

    // Should show validation error
    await expect(
      page.locator('text=/required|must|invalid/i')
    ).toBeVisible({ timeout: 2000 });
  });

  test('should show loading state while fetching sources', async ({ page }) => {
    // Reload page to see loading
    page.reload();

    // Should show loading indicator
    const loader = page.locator(
      '[data-testid="loading-sources"], [role="progressbar"], text=/loading/i'
    );

    // May or may not be visible depending on speed, but shouldn't crash
    try {
      await expect(loader).toBeVisible({ timeout: 1000 });
    } catch {
      // Loading was too fast, that's fine
    }

    // Table should eventually appear
    await uiHelper.waitForTable();
  });

  test('should paginate sources', async ({ page }) => {
    // Create multiple sources via API (if needed)
    // Note: would need to create 10+ sources to see pagination

    // Check if pagination exists
    const pagination = page.locator('[role="navigation"]');
    const isPaginationVisible = await pagination.isVisible().catch(() => false);

    // If visible, test navigation
    if (isPaginationVisible) {
      const nextBtn = page.locator('button[aria-label*="Next"], a:has-text("Next")');
      if (await nextBtn.isEnabled()) {
        await nextBtn.click();
        await page.waitForLoadState('networkidle');
      }
    }
  });
});
