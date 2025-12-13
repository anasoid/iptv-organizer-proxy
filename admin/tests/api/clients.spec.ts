/**
 * Clients API Tests
 * Tests for client CRUD operations
 */

import { test, expect } from '@playwright/test';
import { APIHelper } from '../utils/api-helper';
import { TEST_CLIENT, TEST_CLIENT_2, TEST_SOURCE } from '../fixtures/test-data';

test.describe('Clients API', () => {
  let apiHelper: APIHelper;
  let sourceId: number;

  test.beforeEach(async ({ request }) => {
    apiHelper = new APIHelper(request);
    await apiHelper.login();

    // Create a source for testing
    const sourceResponse = await apiHelper.createSource(TEST_SOURCE);
    sourceId = sourceResponse.data.id;
  });

  test.afterEach(async () => {
    await apiHelper.logout();
  });

  test.describe('GET /api/clients', () => {
    test('should list all clients', async () => {
      const response = await apiHelper.getClients();

      expect(response).toHaveProperty('success');
      expect(Array.isArray(response.data)).toBeTruthy();
    });
  });

  test.describe('POST /api/clients', () => {
    test('should create a new client', async () => {
      const clientData = {
        ...TEST_CLIENT,
        sourceId,
      };

      const response = await apiHelper.createClient(clientData);

      expect(response.success).toBeTruthy();
      expect(response.data).toHaveProperty('id');
      expect(response.data.username).toBe(TEST_CLIENT.username);
    });

    test('should fail creating client without required fields', async () => {
      const response = await apiHelper.post('/api/clients', {
        username: 'incomplete',
        // missing password and sourceId
      });

      expect(response.success).toBeFalsy();
    });

    test('should create multiple clients for same source', async () => {
      const client1 = await apiHelper.createClient({
        ...TEST_CLIENT,
        sourceId,
      });

      const client2 = await apiHelper.createClient({
        ...TEST_CLIENT_2,
        sourceId,
      });

      expect(client1.data.id).toBeDefined();
      expect(client2.data.id).toBeDefined();
      expect(client1.data.id).not.toBe(client2.data.id);
    });
  });

  test.describe('GET /api/clients/:id', () => {
    test('should get a client by ID', async () => {
      const created = await apiHelper.createClient({
        ...TEST_CLIENT,
        sourceId,
      });

      const response = await apiHelper.getClient(created.data.id);

      expect(response.success).toBeTruthy();
      expect(response.data.username).toBe(TEST_CLIENT.username);
    });
  });

  test.describe('PUT /api/clients/:id', () => {
    test('should update a client', async () => {
      const created = await apiHelper.createClient({
        ...TEST_CLIENT,
        sourceId,
      });

      const updateData = {
        ...TEST_CLIENT,
        password: 'newpassword123',
      };

      const response = await apiHelper.updateClient(created.data.id, updateData);

      expect(response.success).toBeTruthy();
      expect(response.data.password).toBe('newpassword123');
    });
  });

  test.describe('DELETE /api/clients/:id', () => {
    test('should delete a client', async () => {
      const created = await apiHelper.createClient({
        ...TEST_CLIENT,
        sourceId,
      });

      const deleteResponse = await apiHelper.deleteClient(created.data.id);
      expect(deleteResponse.success).toBeTruthy();

      // Verify it's gone
      const getResponse = await apiHelper.request.get(
        `http://localhost:8000/api/clients/${created.data.id}`,
        {
          headers: apiHelper.getAuthHeaders(),
        }
      );

      expect(getResponse.status()).toBe(404);
    });
  });

  test.describe('GET /api/clients/:id/logs', () => {
    test('should get client connection logs', async () => {
      const created = await apiHelper.createClient({
        ...TEST_CLIENT,
        sourceId,
      });

      const response = await apiHelper.get(`/api/clients/${created.data.id}/logs`);

      expect(response).toHaveProperty('success');
      expect(Array.isArray(response.data)).toBeTruthy();
    });
  });
});
