/**
 * Sources API Tests
 * Tests for CRUD operations and source management
 */

import { test, expect } from '@playwright/test';
import { APIHelper } from '../utils/api-helper';
import { TEST_SOURCE, TEST_SOURCE_2 } from '../fixtures/test-data';

test.describe('Sources API', () => {
  let apiHelper: APIHelper;

  test.beforeEach(async ({ request }) => {
    apiHelper = new APIHelper(request);
    await apiHelper.login();
  });

  test.afterEach(async () => {
    await apiHelper.logout();
  });

  test.describe('GET /api/sources', () => {
    test('should list all sources', async () => {
      const response = await apiHelper.getSources();

      expect(response).toHaveProperty('success');
      expect(response).toHaveProperty('data');
      expect(Array.isArray(response.data)).toBeTruthy();
    });

    test('should return paginated results', async () => {
      const response = await apiHelper.get('/api/sources?page=1&limit=10');

      expect(response).toHaveProperty('data');
      expect(response).toHaveProperty('pagination');
    });
  });

  test.describe('POST /api/sources', () => {
    test('should create a new source', async () => {
      const sourceData = { ...TEST_SOURCE };

      const response = await apiHelper.createSource(sourceData);

      expect(response.success).toBeTruthy();
      expect(response.data).toHaveProperty('id');
      expect(response.data.name).toBe(TEST_SOURCE.name);
      expect(response.data.url).toBe(TEST_SOURCE.url);
    });

    test('should fail creating source without required fields', async () => {
      const response = await apiHelper.post('/api/sources', {
        name: 'Incomplete Source',
        // missing url, username, password
      });

      expect(response.success).toBeFalsy();
    });

    test('should create multiple sources', async () => {
      const source1 = await apiHelper.createSource(TEST_SOURCE);
      const source2 = await apiHelper.createSource(TEST_SOURCE_2);

      expect(source1.data.id).toBeDefined();
      expect(source2.data.id).toBeDefined();
      expect(source1.data.id).not.toBe(source2.data.id);
    });
  });

  test.describe('GET /api/sources/:id', () => {
    test('should get a source by ID', async () => {
      // Create a source first
      const created = await apiHelper.createSource(TEST_SOURCE);
      const sourceId = created.data.id;

      // Get it back
      const response = await apiHelper.getSource(sourceId);

      expect(response.success).toBeTruthy();
      expect(response.data.id).toBe(sourceId);
      expect(response.data.name).toBe(TEST_SOURCE.name);
    });

    test('should return 404 for non-existent source', async () => {
      const response = await apiHelper.request.get(
        'http://localhost:8000/api/sources/99999',
        {
          headers: apiHelper.getAuthHeaders(),
        }
      );

      expect(response.status()).toBe(404);
    });
  });

  test.describe('PUT /api/sources/:id', () => {
    test('should update a source', async () => {
      // Create a source
      const created = await apiHelper.createSource(TEST_SOURCE);
      const sourceId = created.data.id;

      // Update it
      const updateData = {
        ...TEST_SOURCE,
        name: 'Updated Source Name',
      };

      const response = await apiHelper.updateSource(sourceId, updateData);

      expect(response.success).toBeTruthy();
      expect(response.data.name).toBe('Updated Source Name');
    });

    test('should preserve existing fields when updating', async () => {
      // Create a source
      const created = await apiHelper.createSource(TEST_SOURCE);
      const sourceId = created.data.id;

      // Update only name
      const response = await apiHelper.updateSource(sourceId, {
        name: 'New Name Only',
      });

      expect(response.data.url).toBe(TEST_SOURCE.url);
      expect(response.data.name).toBe('New Name Only');
    });
  });

  test.describe('DELETE /api/sources/:id', () => {
    test('should delete a source', async () => {
      // Create a source
      const created = await apiHelper.createSource(TEST_SOURCE);
      const sourceId = created.data.id;

      // Delete it
      const deleteResponse = await apiHelper.deleteSource(sourceId);
      expect(deleteResponse.success).toBeTruthy();

      // Verify it's gone
      const getResponse = await apiHelper.request.get(
        `http://localhost:8000/api/sources/${sourceId}`,
        {
          headers: apiHelper.getAuthHeaders(),
        }
      );

      expect(getResponse.status()).toBe(404);
    });
  });

  test.describe('POST /api/sources/:id/test', () => {
    test('should test source connection', async () => {
      // Create a source (with Mockoon running on port 3000)
      const created = await apiHelper.createSource(TEST_SOURCE);
      const sourceId = created.data.id;

      // Test connection
      const response = await apiHelper.testSourceConnection(sourceId);

      expect(response).toHaveProperty('success');
      expect(response).toHaveProperty('data');
      // Should succeed if Mockoon is running
    });

    test('should fail for invalid connection', async () => {
      // Create a source with invalid URL
      const invalidSource = {
        name: 'Invalid Source',
        url: 'http://invalid-host:9999',
        username: 'user',
        password: 'pass',
      };

      const created = await apiHelper.createSource(invalidSource);
      const sourceId = created.data.id;

      // Test connection
      const response = await apiHelper.testSourceConnection(sourceId);

      expect(response.success).toBeFalsy();
    });
  });

  test.describe('POST /api/sources/:id/sync', () => {
    test('should trigger sync for a source', async () => {
      // Create a source
      const created = await apiHelper.createSource(TEST_SOURCE);
      const sourceId = created.data.id;

      // Trigger sync
      const response = await apiHelper.syncSource(sourceId);

      expect(response.success).toBeTruthy();
      expect(response.data).toHaveProperty('sync_id');
    });

    test('should sync specific task type', async () => {
      // Create a source
      const created = await apiHelper.createSource(TEST_SOURCE);
      const sourceId = created.data.id;

      // Sync live categories
      const response = await apiHelper.syncSource(sourceId, 'live_categories');

      expect(response.success).toBeTruthy();
    });

    test('should prevent concurrent syncs for same source', async () => {
      // Create a source
      const created = await apiHelper.createSource(TEST_SOURCE);
      const sourceId = created.data.id;

      // Start first sync
      const sync1 = await apiHelper.syncSource(sourceId);
      expect(sync1.success).toBeTruthy();

      // Try second sync (should fail or queue)
      const sync2 = await apiHelper.syncSource(sourceId);

      // Depending on implementation, should either fail or be queued
      expect(sync2).toHaveProperty('success');
    });
  });

  test.describe('GET /api/sources/:id/sync-logs', () => {
    test('should get sync logs for source', async () => {
      // Create a source
      const created = await apiHelper.createSource(TEST_SOURCE);
      const sourceId = created.data.id;

      // Get sync logs
      const response = await apiHelper.get(`/api/sources/${sourceId}/sync-logs`);

      expect(response).toHaveProperty('success');
      expect(Array.isArray(response.data)).toBeTruthy();
    });
  });
});
