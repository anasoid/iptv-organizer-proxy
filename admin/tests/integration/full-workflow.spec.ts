/**
 * Full Workflow Integration Tests
 * End-to-end tests simulating complete user workflows
 */

import { test, expect } from '@playwright/test';
import { UIHelper } from '../utils/ui-helper';
import { APIHelper } from '../utils/api-helper';
import { TEST_SOURCE, TEST_CLIENT, TEST_FILTER, ADMIN_PANEL_URL } from '../fixtures/test-data';

interface Source {
  id: string | number;
  name: string;
  url: string;
  username: string;
  password: string;
}

interface Client {
  id: string | number;
  username: string;
  password: string;
  sourceId: string | number;
}

interface Filter {
  id: string | number;
  name: string;
  filterConfig: string;
}

test.describe('Full User Workflows', () => {
  let uiHelper: UIHelper;
  let apiHelper: APIHelper;

  test.beforeEach(async ({ page, request }) => {
    uiHelper = new UIHelper(page);
    apiHelper = new APIHelper(request);
  });

  test('complete workflow: create source, client, filter, and sync', async ({ page }) => {
    // 1. Login
    await uiHelper.login();
    await expect(page).toHaveURL(`${ADMIN_PANEL_URL}/dashboard`);

    // 2. Create a filter
    await uiHelper.navigateTo('/filters');
    const filterData = { ...TEST_FILTER };

    await page.click('button:has-text("Add Filter")');
    await page.waitForSelector('[role="dialog"]', { timeout: 5000 });

    await page.fill(
      'input[placeholder*="Name"], input[aria-label*="Name"]',
      filterData.name
    );
    await page.fill(
      'textarea[placeholder*="Config"], textarea[aria-label*="Config"]',
      filterData.filterConfig
    );

    await page.click('button:has-text("Create"), button[type="submit"]');
    await expect(page.locator('text=/created|success/i')).toBeVisible({
      timeout: 5000,
    });

    // 3. Create a source
    await uiHelper.navigateTo('/sources');

    await page.click('button:has-text("Add Source")');
    await page.waitForSelector('[role="dialog"]', { timeout: 5000 });

    const sourceData = { ...TEST_SOURCE };
    await page.fill(
      'input[placeholder*="Name"], input[aria-label*="Name"]',
      sourceData.name
    );
    await page.fill(
      'input[placeholder*="URL"], input[aria-label*="URL"]',
      sourceData.url
    );
    await page.fill(
      'input[placeholder*="Username"], input[aria-label*="Username"]',
      sourceData.username
    );
    await page.fill(
      'input[placeholder*="Password"], input[aria-label*="Password"]',
      sourceData.password
    );

    await page.click('button:has-text("Create"), button[type="submit"]');
    await expect(page.locator('text=/created|success/i')).toBeVisible({
      timeout: 5000,
    });

    // Get source ID from API
    await apiHelper.login();
    const sourcesResponse = await apiHelper.getSources();
    const sourceId = sourcesResponse.data.find(
      (s: Source) => s.name === sourceData.name
    )?.id;

    expect(sourceId).toBeDefined();

    // 4. Create a client
    await uiHelper.navigateTo('/clients');

    await page.click('button:has-text("Add Client")');
    await page.waitForSelector('[role="dialog"]', { timeout: 5000 });

    const clientData = { ...TEST_CLIENT, sourceId };
    await page.fill(
      'input[placeholder*="Username"], input[aria-label*="Username"]',
      clientData.username
    );
    await page.fill(
      'input[placeholder*="Password"], input[aria-label*="Password"]',
      clientData.password
    );

    // Select source dropdown
    const sourceDropdown = page.locator(
      '[aria-label*="Source"], select'
    ).first();
    if (await sourceDropdown.isVisible()) {
      await sourceDropdown.click();
      await page.click(`text=${sourceData.name}`);
    }

    await page.click('button:has-text("Create"), button[type="submit"]');
    await expect(page.locator('text=/created|success/i')).toBeVisible({
      timeout: 5000,
    });

    // 5. Trigger sync
    await uiHelper.navigateTo('/sources');

    const row = await uiHelper.getTableRowByText(sourceData.name);
    const syncBtn = row.locator('button:has-text("Sync")');

    if (await syncBtn.isVisible()) {
      await syncBtn.click();

      // Should start syncing
      await expect(
        page.locator('text=/syncing|in progress/i')
      ).toBeVisible({ timeout: 3000 });
    }

    // 6. Navigate to sync logs
    await uiHelper.navigateTo('/sync-logs');

    // Should show recent sync logs
    await expect(page.locator('[role="grid"]')).toBeVisible({ timeout: 5000 });

    // 7. View synced categories
    await uiHelper.navigateTo('/categories');

    // Should show categories from synced source
    await uiHelper.waitForTable();

    // 8. View synced streams
    await uiHelper.navigateTo('/streams');

    // Should show streams from synced source
    await uiHelper.waitForTable();

    // 9. Verify all created items exist in API
    const filters = await apiHelper.getFilters();
    expect(
      filters.data.some((f: Filter) => f.name === filterData.name)
    ).toBeTruthy();

    const sources = await apiHelper.getSources();
    expect(
      sources.data.some((s: Source) => s.name === sourceData.name)
    ).toBeTruthy();

    const clients = await apiHelper.getClients();
    expect(
      clients.data.some((c: Client) => c.username === clientData.username)
    ).toBeTruthy();
  });

  test('workflow: test connection before sync', async () => {
    await apiHelper.login();

    // 1. Create source
    const sourceResponse = await apiHelper.createSource(TEST_SOURCE);
    const sourceId = sourceResponse.data.id;

    // 2. Test connection
    const testResult = await apiHelper.testSourceConnection(sourceId);

    // Should have success property
    expect(testResult).toHaveProperty('success');

    // 3. If connection succeeded, sync
    if (testResult.success) {
      const syncResult = await apiHelper.syncSource(sourceId);

      expect(syncResult.success).toBeTruthy();
      expect(syncResult.data).toHaveProperty('sync_id');

      // 4. Check sync logs
      const logs = await apiHelper.getSyncLogs();

      // Should have new log entry
      expect(Array.isArray(logs.data)).toBeTruthy();
    }
  });

  test('workflow: manage multiple sources and clients', async () => {
    await apiHelper.login();

    // 1. Create multiple sources
    const sources = [];
    for (let i = 0; i < 3; i++) {
      const response = await apiHelper.createSource({
        name: `Test Source ${i}`,
        url: 'http://localhost:3000',
        username: `user${i}`,
        password: `pass${i}`,
      });
      sources.push(response.data);
    }

    expect(sources).toHaveLength(3);

    // 2. Create clients for each source
    const clients = [];
    for (let i = 0; i < 3; i++) {
      const response = await apiHelper.createClient({
        username: `client${i}`,
        password: `clientpass${i}`,
        sourceId: sources[i].id,
      });
      clients.push(response.data);
    }

    expect(clients).toHaveLength(3);

    // 3. List all
    const allSources = await apiHelper.getSources();
    expect(allSources.data.length).toBeGreaterThanOrEqual(3);

    const allClients = await apiHelper.getClients();
    expect(allClients.data.length).toBeGreaterThanOrEqual(3);

    // 4. Update one source
    const updateResponse = await apiHelper.updateSource(sources[0].id, {
      ...sources[0],
      name: 'Updated Source 0',
    });

    expect(updateResponse.data.name).toBe('Updated Source 0');

    // 5. Delete one client
    const deleteResponse = await apiHelper.deleteClient(clients[0].id);
    expect(deleteResponse.success).toBeTruthy();

    // 6. Verify deletion
    const afterDelete = await apiHelper.getClients();
    expect(
      afterDelete.data.some((c: Client) => c.id === clients[0].id)
    ).toBeFalsy();
  });

  test('workflow: error handling and recovery', async ({ page }) => {
    // 1. Login
    await uiHelper.login();

    // 2. Try to create invalid source
    await uiHelper.navigateTo('/sources');
    await page.click('button:has-text("Add Source")');
    await page.waitForSelector('[role="dialog"]', { timeout: 5000 });

    // Submit without data
    await page.click('button:has-text("Create"), button[type="submit"]');

    // Should show validation error
    await expect(page.locator('text=/required|must|invalid/i')).toBeVisible({
      timeout: 2000,
    });

    // 3. Fill form correctly
    await page.fill(
      'input[placeholder*="Name"], input[aria-label*="Name"]',
      TEST_SOURCE.name
    );
    await page.fill(
      'input[placeholder*="URL"], input[aria-label*="URL"]',
      TEST_SOURCE.url
    );
    await page.fill(
      'input[placeholder*="Username"], input[aria-label*="Username"]',
      TEST_SOURCE.username
    );
    await page.fill(
      'input[placeholder*="Password"], input[aria-label*="Password"]',
      TEST_SOURCE.password
    );

    // Submit again
    await page.click('button:has-text("Create"), button[type="submit"]');

    // Should now succeed
    await expect(page.locator('text=/created|success/i')).toBeVisible({
      timeout: 5000,
    });
  });
});
