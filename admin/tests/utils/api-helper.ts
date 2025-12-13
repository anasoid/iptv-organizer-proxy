/**
 * API Helper Utilities
 * Common API operations for testing
 */

import { APIRequestContext } from '@playwright/test';
import { API_BASE_URL, TEST_ADMIN_USER } from '../fixtures/test-data';

export class APIHelper {
  private baseUrl = API_BASE_URL;
  private request: APIRequestContext;
  private token: string | null = null;

  constructor(request: APIRequestContext) {
    this.request = request;
  }

  /**
   * Login and get JWT token
   */
  async login(
    username: string = TEST_ADMIN_USER.username,
    password: string = TEST_ADMIN_USER.password
  ): Promise<string> {
    const response = await this.request.post(`${this.baseUrl}/api/auth/login`, {
      data: { username, password },
    });

    if (!response.ok()) {
      throw new Error(`Login failed: ${response.status()}`);
    }

    const body = await response.json();
    this.token = body.token;
    return this.token;
  }

  /**
   * Get authorization headers
   */
  getAuthHeaders(): Record<string, string> {
    if (!this.token) {
      throw new Error('Not authenticated. Call login() first');
    }

    return {
      Authorization: `Bearer ${this.token}`,
      'Content-Type': 'application/json',
    };
  }

  /**
   * GET request with auth
   */
  async get<T = any>(endpoint: string): Promise<T> {
    const response = await this.request.get(`${this.baseUrl}${endpoint}`, {
      headers: this.getAuthHeaders(),
    });

    if (!response.ok()) {
      throw new Error(`GET ${endpoint} failed: ${response.status()}`);
    }

    return response.json();
  }

  /**
   * POST request with auth
   */
  async post<T = any>(endpoint: string, data: any): Promise<T> {
    const response = await this.request.post(`${this.baseUrl}${endpoint}`, {
      headers: this.getAuthHeaders(),
      data,
    });

    if (!response.ok()) {
      throw new Error(`POST ${endpoint} failed: ${response.status()}`);
    }

    return response.json();
  }

  /**
   * PUT request with auth
   */
  async put<T = any>(endpoint: string, data: any): Promise<T> {
    const response = await this.request.put(`${this.baseUrl}${endpoint}`, {
      headers: this.getAuthHeaders(),
      data,
    });

    if (!response.ok()) {
      throw new Error(`PUT ${endpoint} failed: ${response.status()}`);
    }

    return response.json();
  }

  /**
   * DELETE request with auth
   */
  async delete<T = any>(endpoint: string): Promise<T> {
    const response = await this.request.delete(`${this.baseUrl}${endpoint}`, {
      headers: this.getAuthHeaders(),
    });

    if (!response.ok()) {
      throw new Error(`DELETE ${endpoint} failed: ${response.status()}`);
    }

    return response.json();
  }

  /**
   * Create a source
   */
  async createSource(sourceData: any) {
    return this.post('/api/sources', sourceData);
  }

  /**
   * Get all sources
   */
  async getSources() {
    return this.get('/api/sources');
  }

  /**
   * Get source by ID
   */
  async getSource(id: number) {
    return this.get(`/api/sources/${id}`);
  }

  /**
   * Update source
   */
  async updateSource(id: number, data: any) {
    return this.put(`/api/sources/${id}`, data);
  }

  /**
   * Delete source
   */
  async deleteSource(id: number) {
    return this.delete(`/api/sources/${id}`);
  }

  /**
   * Test source connection
   */
  async testSourceConnection(id: number) {
    return this.post(`/api/sources/${id}/test`, {});
  }

  /**
   * Trigger sync
   */
  async syncSource(id: number, taskType?: string) {
    if (taskType) {
      return this.post(`/api/sources/${id}/sync/${taskType}`, {});
    }
    return this.post(`/api/sources/${id}/sync`, {});
  }

  /**
   * Get sync logs
   */
  async getSyncLogs() {
    return this.get('/api/sync-logs');
  }

  /**
   * Create a client
   */
  async createClient(clientData: any) {
    return this.post('/api/clients', clientData);
  }

  /**
   * Get all clients
   */
  async getClients() {
    return this.get('/api/clients');
  }

  /**
   * Get client by ID
   */
  async getClient(id: number) {
    return this.get(`/api/clients/${id}`);
  }

  /**
   * Update client
   */
  async updateClient(id: number, data: any) {
    return this.put(`/api/clients/${id}`, data);
  }

  /**
   * Delete client
   */
  async deleteClient(id: number) {
    return this.delete(`/api/clients/${id}`);
  }

  /**
   * Create a filter
   */
  async createFilter(filterData: any) {
    return this.post('/api/filters', filterData);
  }

  /**
   * Get all filters
   */
  async getFilters() {
    return this.get('/api/filters');
  }

  /**
   * Get filter by ID
   */
  async getFilter(id: number) {
    return this.get(`/api/filters/${id}`);
  }

  /**
   * Update filter
   */
  async updateFilter(id: number, data: any) {
    return this.put(`/api/filters/${id}`, data);
  }

  /**
   * Delete filter
   */
  async deleteFilter(id: number) {
    return this.delete(`/api/filters/${id}`);
  }

  /**
   * Get dashboard stats
   */
  async getDashboardStats() {
    return this.get('/api/dashboard/stats');
  }

  /**
   * Get categories
   */
  async getCategories() {
    return this.get('/api/categories');
  }

  /**
   * Get streams
   */
  async getStreams() {
    return this.get('/api/streams');
  }

  /**
   * Logout
   */
  async logout() {
    await this.post('/api/auth/logout', {});
    this.token = null;
  }
}
