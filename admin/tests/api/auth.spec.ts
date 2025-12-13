/**
 * Authentication API Tests
 */

import { test, expect } from '@playwright/test';
import { APIHelper } from '../utils/api-helper';
import { TEST_ADMIN_USER, API_BASE_URL } from '../fixtures/test-data';

test.describe('Authentication API', () => {
  let apiHelper: APIHelper;

  test.beforeEach(async ({ request }) => {
    apiHelper = new APIHelper(request);
  });

  test('should login with valid credentials', async () => {
    const response = await apiHelper.login();

    expect(response).toBeTruthy();
    expect(typeof response).toBe('string');
  });

  test('should fail login with invalid credentials', async ({ request }) => {
    const response = await request.post(`${API_BASE_URL}/api/auth/login`, {
      data: {
        username: 'invalid',
        password: 'wrong',
      },
    });

    expect(response.status()).toBe(401);
  });

  test('should return user info after login', async ({ request }) => {
    await apiHelper.login();

    const response = await request.get(`${API_BASE_URL}/api/auth/me`, {
      headers: apiHelper.getAuthHeaders(),
    });

    expect(response.ok()).toBeTruthy();
    const data = await response.json();
    expect(data).toHaveProperty('id');
    expect(data).toHaveProperty('username');
    expect(data.username).toBe(TEST_ADMIN_USER.username);
  });

  test('should logout successfully', async () => {
    await apiHelper.login();
    await apiHelper.logout();

    // Token should be cleared
    expect(() => apiHelper.getAuthHeaders()).toThrow();
  });

  test('should reject unauthenticated requests', async ({ request }) => {
    const response = await request.get(`${API_BASE_URL}/api/sources`);

    expect(response.status()).toBe(401);
  });
});
