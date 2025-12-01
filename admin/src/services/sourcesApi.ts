import api from './api';

export interface Source {
  id?: number;
  name: string;
  url: string;
  username: string;
  password: string;
  sync_interval: number;
  sync_status: 'idle' | 'syncing' | 'error';
  last_sync?: string | null;
  next_sync?: string | null;
  is_active: number;
  created_at?: string;
  updated_at?: string;
}

export interface SyncLog {
  id: number;
  source_id: number;
  task_type: string;
  status: string;
  started_at: string;
  completed_at?: string;
  details?: string;
}

export interface SyncStatus {
  id: number;
  name: string;
  sync_status: 'idle' | 'syncing' | 'error';
  last_sync: string | null;
  next_sync: string | null;
  sync_interval: number;
}

class SourcesApi {
  /**
   * Get all sources with pagination
   */
  async getSources(page: number = 1, limit: number = 10) {
    const response = await api.get('/sources', {
      params: { page, limit },
    });
    return response.data;
  }

  /**
   * Get single source by ID
   */
  async getSource(id: number) {
    const response = await api.get(`/sources/${id}`);
    return response.data;
  }

  /**
   * Create new source
   */
  async createSource(source: Omit<Source, 'id' | 'created_at' | 'updated_at'>) {
    const response = await api.post('/sources', source);
    return response.data;
  }

  /**
   * Update existing source
   */
  async updateSource(id: number, source: Partial<Source>) {
    const response = await api.put(`/sources/${id}`, source);
    return response.data;
  }

  /**
   * Delete source
   */
  async deleteSource(id: number) {
    const response = await api.delete(`/sources/${id}`);
    return response.data;
  }

  /**
   * Test source connection
   */
  async testConnection(id: number) {
    const response = await api.post(`/sources/${id}/test`);
    return response.data;
  }

  /**
   * Trigger manual sync
   */
  async triggerSync(id: number) {
    const response = await api.post(`/sources/${id}/sync`);
    return response.data;
  }

  /**
   * Get sync logs for source
   */
  async getSyncLogs(id: number, limit: number = 50) {
    const response = await api.get(`/sources/${id}/sync-logs`, {
      params: { limit },
    });
    return response.data;
  }

  /**
   * Get sync status for all sources
   */
  async getSyncStatus() {
    const response = await api.get('/sync/status');
    return response.data;
  }
}

export default new SourcesApi();
