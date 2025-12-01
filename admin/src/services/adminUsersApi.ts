import { api } from './api';

export interface AdminUser {
  id: number;
  username: string;
  email?: string;
  is_active: number;
  created_at?: string;
  updated_at?: string;
}

export interface AdminUserResponse {
  success: boolean;
  data?: AdminUser | AdminUser[];
  message?: string;
  pagination?: {
    page: number;
    limit: number;
    total: number;
    pages: number;
  };
}

const adminUsersApi = {
  async getAdminUsers(page: number = 1, limit: number = 10): Promise<AdminUserResponse> {
    const response = await api.get('/admin-users', {
      params: { page, limit },
    });
    return response.data;
  },

  async getAdminUser(id: number): Promise<AdminUserResponse> {
    const response = await api.get(`/admin-users/${id}`);
    return response.data;
  },

  async createAdminUser(data: {
    username: string;
    password: string;
    email?: string;
  }): Promise<AdminUserResponse> {
    const response = await api.post('/admin-users', data);
    return response.data;
  },

  async updateAdminUser(
    id: number,
    data: {
      username?: string;
      email?: string;
      is_active?: number;
    }
  ): Promise<AdminUserResponse> {
    const response = await api.put(`/admin-users/${id}`, data);
    return response.data;
  },

  async deleteAdminUser(id: number): Promise<AdminUserResponse> {
    const response = await api.delete(`/admin-users/${id}`);
    return response.data;
  },
};

export default adminUsersApi;
