import api from './api';

export interface Client {
  id?: number;
  username: string;
  password: string;
  source_id: number;
  filter_id?: number | null;
  email?: string | null;
  is_active: number;
  hide_adult_content?: number;
  created_at?: string;
  updated_at?: string;
}

export interface ConnectionLog {
  id: number;
  client_id: number;
  action: string;
  timestamp: string;
  ip_address?: string;
  user_agent?: string;
}

class ClientsApi {
  /**
   * Get all clients with pagination and search
   */
  async getClients(page: number = 1, limit: number = 10, search?: string) {
    const response = await api.get('/clients', {
      params: { page, limit, search },
    });
    return response.data;
  }

  /**
   * Get single client by ID
   */
  async getClient(id: number) {
    const response = await api.get(`/clients/${id}`);
    return response.data;
  }

  /**
   * Create new client
   */
  async createClient(client: Omit<Client, 'id' | 'created_at' | 'updated_at'>) {
    const response = await api.post('/clients', client);
    return response.data;
  }

  /**
   * Update existing client
   */
  async updateClient(id: number, client: Partial<Client>) {
    const response = await api.put(`/clients/${id}`, client);
    return response.data;
  }

  /**
   * Delete client
   */
  async deleteClient(id: number) {
    const response = await api.delete(`/clients/${id}`);
    return response.data;
  }

  /**
   * Get connection logs for client
   */
  async getClientLogs(id: number, limit: number = 50) {
    const response = await api.get(`/clients/${id}/logs`, {
      params: { limit },
    });
    return response.data;
  }

  /**
   * Export live categories for client
   */
  async exportLiveCategories(id: number) {
    const response = await api.get(`/clients/${id}/export/live-categories`);
    return response.data;
  }

  /**
   * Export VOD categories for client
   */
  async exportVodCategories(id: number) {
    const response = await api.get(`/clients/${id}/export/vod-categories`);
    return response.data;
  }

  /**
   * Export series categories for client
   */
  async exportSeriesCategories(id: number) {
    const response = await api.get(`/clients/${id}/export/series-categories`);
    return response.data;
  }

  /**
   * Export live streams for client (optionally filtered by category)
   */
  async exportLiveStreams(id: number, categoryId?: number) {
    const params = categoryId ? { category_id: categoryId } : {};
    const response = await api.get(`/clients/${id}/export/live-streams`, { params });
    return response.data;
  }

  /**
   * Export VOD streams for client (optionally filtered by category)
   */
  async exportVodStreams(id: number, categoryId?: number) {
    const params = categoryId ? { category_id: categoryId } : {};
    const response = await api.get(`/clients/${id}/export/vod-streams`, { params });
    return response.data;
  }

  /**
   * Export series for client (optionally filtered by category)
   */
  async exportSeries(id: number, categoryId?: number) {
    const params = categoryId ? { category_id: categoryId } : {};
    const response = await api.get(`/clients/${id}/export/series`, { params });
    return response.data;
  }

  /**
   * Export blocked live categories for client
   */
  async exportBlockedLiveCategories(id: number) {
    const response = await api.get(`/clients/${id}/export/blocked-live-categories`);
    return response.data;
  }

  /**
   * Export blocked VOD categories for client
   */
  async exportBlockedVodCategories(id: number) {
    const response = await api.get(`/clients/${id}/export/blocked-vod-categories`);
    return response.data;
  }

  /**
   * Export blocked series categories for client
   */
  async exportBlockedSeriesCategories(id: number) {
    const response = await api.get(`/clients/${id}/export/blocked-series-categories`);
    return response.data;
  }

  /**
   * Export blocked live streams for client
   */
  async exportBlockedLiveStreams(id: number) {
    const response = await api.get(`/clients/${id}/export/blocked-live-streams`);
    return response.data;
  }

  /**
   * Export blocked VOD streams for client
   */
  async exportBlockedVodStreams(id: number) {
    const response = await api.get(`/clients/${id}/export/blocked-vod-streams`);
    return response.data;
  }

  /**
   * Export blocked series for client
   */
  async exportBlockedSeries(id: number) {
    const response = await api.get(`/clients/${id}/export/blocked-series`);
    return response.data;
  }
}

export default new ClientsApi();
