/**
 * Test Data Fixtures
 * Common test data used across test suites
 */

export const TEST_ADMIN_USER = {
  username: 'admin',
  password: 'admin',
  email: 'admin@example.com',
};

export const TEST_SOURCE = {
  name: 'Test Source',
  url: 'http://localhost:3000',
  username: 'testuser',
  password: 'testpass',
};

export const TEST_SOURCE_2 = {
  name: 'Test Source 2',
  url: 'http://localhost:3000',
  username: 'testuser2',
  password: 'testpass2',
};

export const TEST_CLIENT = {
  username: 'client1',
  password: 'clientpass123',
  sourceId: 1,
};

export const TEST_CLIENT_2 = {
  username: 'client2',
  password: 'clientpass456',
  sourceId: 1,
};

export const TEST_FILTER = {
  name: 'Adult Filter',
  description: 'Filters out adult content',
  filterConfig: `
rules:
  - action: exclude
    field: is_adult
    value: 1
`,
};

export const TEST_FILTER_2 = {
  name: 'HD Only Filter',
  description: 'Only includes HD streams',
  filterConfig: `
rules:
  - action: include
    field: name
    regex: '(HD|1080p|720p)'
`,
};

export const API_BASE_URL = 'http://localhost:8000';
export const ADMIN_PANEL_URL = 'http://localhost:5173/admin';

/**
 * Delays for testing async operations
 */
export const DELAYS = {
  SHORT: 500,
  MEDIUM: 1000,
  LONG: 2000,
  VERY_LONG: 5000,
};
