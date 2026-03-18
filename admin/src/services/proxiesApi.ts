import api from './api';

export interface Proxy {
  id?: number;
  name: string;
  description?: string | null;
  proxyUrl?: string | null;
  proxyHost?: string | null;
  proxyPort?: number | null;
  proxyType?: 'HTTP' | 'HTTPS' | 'SOCKS5' | null;
  proxyUsername?: string | null;
  proxyPassword?: string | null;
  timeout?: number | null;
  maxRetries?: number | null;
  createdAt?: string;
  updatedAt?: string;
}

class ProxiesApi {
  /**
   * Get all proxies with pagination
   */
  async getProxies(page: number = 1, limit: number = 10) {
    const response = await api.get('/proxies', {
      params: { page, limit },
    });
    return response.data;
  }

  /**
   * Get single proxy by ID
   */
  async getProxy(id: number) {
    const response = await api.get(`/proxies/${id}`);
    return response.data;
  }

  /**
   * Create new proxy
   */
  async createProxy(proxy: Omit<Proxy, 'id' | 'createdAt' | 'updatedAt'>) {
    const response = await api.post('/proxies', proxy);
    return response.data;
  }

  /**
   * Update existing proxy
   */
  async updateProxy(id: number, proxy: Partial<Proxy>) {
    const response = await api.put(`/proxies/${id}`, proxy);
    return response.data;
  }

  /**
   * Delete proxy
   */
  async deleteProxy(id: number) {
    const response = await api.delete(`/proxies/${id}`);
    return response.data;
  }
}

export default new ProxiesApi();
