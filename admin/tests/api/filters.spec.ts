/**
 * Filters API Tests
 * Tests for filter CRUD operations
 */

import { test, expect } from '@playwright/test';
import { APIHelper } from '../utils/api-helper';
import { TEST_FILTER, TEST_FILTER_2 } from '../fixtures/test-data';

test.describe('Filters API', () => {
  let apiHelper: APIHelper;

  test.beforeEach(async ({ request }) => {
    apiHelper = new APIHelper(request);
    await apiHelper.login();
  });

  test.afterEach(async () => {
    await apiHelper.logout();
  });

  test.describe('GET /api/filters', () => {
    test('should list all filters', async () => {
      const response = await apiHelper.getFilters();

      expect(response).toHaveProperty('success');
      expect(Array.isArray(response.data)).toBeTruthy();
    });
  });

  test.describe('POST /api/filters', () => {
    test('should create a new filter', async () => {
      const response = await apiHelper.createFilter(TEST_FILTER);

      expect(response.success).toBeTruthy();
      expect(response.data).toHaveProperty('id');
      expect(response.data.name).toBe(TEST_FILTER.name);
      expect(response.data.description).toBe(TEST_FILTER.description);
    });

    test('should fail creating filter without required fields', async () => {
      const response = await apiHelper.post('/api/filters', {
        name: 'Incomplete Filter',
        // missing description and filterConfig
      });

      expect(response.success).toBeFalsy();
    });

    test('should validate YAML filter config', async () => {
      const invalidFilter = {
        name: 'Invalid Filter',
        description: 'Invalid YAML',
        filterConfig: 'this: is: not: valid: yaml:',
      };

      const response = await apiHelper.createFilter(invalidFilter);

      expect(response.success).toBeFalsy();
      expect(response).toHaveProperty('error');
    });

    test('should create multiple filters', async () => {
      const filter1 = await apiHelper.createFilter(TEST_FILTER);
      const filter2 = await apiHelper.createFilter(TEST_FILTER_2);

      expect(filter1.data.id).toBeDefined();
      expect(filter2.data.id).toBeDefined();
      expect(filter1.data.id).not.toBe(filter2.data.id);
    });
  });

  test.describe('GET /api/filters/:id', () => {
    test('should get a filter by ID', async () => {
      const created = await apiHelper.createFilter(TEST_FILTER);

      const response = await apiHelper.getFilter(created.data.id);

      expect(response.success).toBeTruthy();
      expect(response.data.name).toBe(TEST_FILTER.name);
    });
  });

  test.describe('PUT /api/filters/:id', () => {
    test('should update a filter', async () => {
      const created = await apiHelper.createFilter(TEST_FILTER);

      const updateData = {
        ...TEST_FILTER,
        name: 'Updated Filter Name',
      };

      const response = await apiHelper.updateFilter(created.data.id, updateData);

      expect(response.success).toBeTruthy();
      expect(response.data.name).toBe('Updated Filter Name');
    });

    test('should validate YAML on update', async () => {
      const created = await apiHelper.createFilter(TEST_FILTER);

      const invalidUpdate = {
        ...TEST_FILTER,
        filterConfig: 'invalid: yaml:',
      };

      const response = await apiHelper.updateFilter(created.data.id, invalidUpdate);

      expect(response.success).toBeFalsy();
    });
  });

  test.describe('DELETE /api/filters/:id', () => {
    test('should delete a filter', async () => {
      const created = await apiHelper.createFilter(TEST_FILTER);

      const deleteResponse = await apiHelper.deleteFilter(created.data.id);
      expect(deleteResponse.success).toBeTruthy();

      // Verify it's gone
      const getResponse = await apiHelper.request.get(
        `http://localhost:8000/api/filters/${created.data.id}`,
        {
          headers: apiHelper.getAuthHeaders(),
        }
      );

      expect(getResponse.status()).toBe(404);
    });
  });
});
