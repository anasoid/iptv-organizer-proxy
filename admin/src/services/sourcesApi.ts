import api from './api';

export interface Source {
  id?: number;
  name: string;
  url: string;
  username: string;
  password: string;
  syncInterval: number;
  syncStatus?: 'idle' | 'syncing' | 'error';
  lastSync?: string | null;
  nextSync?: string | null;
  isActive: number;
  proxyId?: number | null;
  enableProxy?: number | null;
  disableStreamProxy?: number | null;
  streamFollowLocation?: number | null;
  useRedirect?: number | null;
  useRedirectXmltv?: number | null;
  createdAt?: string;
  updatedAt?: string;
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

  /**
   * Trigger sync for specific task type
   */
  async triggerSyncTaskType(id: number, taskType: string) {
    const response = await api.post(`/sources/${id}/sync/${taskType}`);
    return response.data;
  }

  /**
   * Trigger sync for all task types in correct order
   */
  async triggerSyncAll(id: number) {
    const response = await api.post(`/sources/${id}/sync-all`);
    return response.data;
  }
}

export const SYNC_TASK_TYPES = [
  { id: 'live_categories', label: 'Live Categories' },
  { id: 'live_streams', label: 'Live Streams' },
  { id: 'vod_categories', label: 'VOD Categories' },
  { id: 'vod_streams', label: 'VOD Streams' },
  { id: 'series_categories', label: 'Series Categories' },
  { id: 'series', label: 'Series' },
] as const;

export default new SourcesApi();
