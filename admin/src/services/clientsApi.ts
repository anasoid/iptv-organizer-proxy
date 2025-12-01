import api from './api';

export interface Client {
  id?: number;
  username: string;
  password: string;
  source_id: number;
  filter_id?: number | null;
  email?: string | null;
  is_active: number;
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
}

export default new ClientsApi();
