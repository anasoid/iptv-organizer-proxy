import api from './api';

export interface SyncLog {
  id: number;
  source_id: number;
  source_name?: string;
  sync_type: string;
  started_at: string;
  completed_at?: string | null;
  duration_seconds?: number | null;
  status: 'running' | 'completed' | 'failed';
  items_added: number;
  items_updated: number;
  items_deleted: number;
  error_message?: string | null;
}

export interface SyncLogStats {
  total_syncs: number;
  completed_syncs: number;
  failed_syncs: number;
  running_syncs?: number;
  total_added: number;
  total_updated: number;
  total_deleted: number;
}

export interface SyncLogsResponse {
  success: boolean;
  data: SyncLog[];
  pagination: {
    page: number;
    limit: number;
    total: number;
    pages: number;
  };
}

export interface SyncLogDetailResponse {
  success: boolean;
  data: SyncLog & {
    source?: {
      id: number;
      name: string;
      url: string;
    };
  };
}

export interface SyncLogStatsResponse {
  success: boolean;
  data: SyncLogStats;
}

class SyncLogsApi {
  /**
   * Get all sync logs with pagination and filters
   */
  async getSyncLogs(
    page: number = 1,
    limit: number = 10,
    filters?: {
      source_id?: number;
      sync_type?: string;
      status?: string;
    }
  ): Promise<SyncLogsResponse> {
    const response = await api.get('/sync-logs', {
      params: { page, limit, ...filters },
    });
    return response.data;
  }

  /**
   * Get single sync log by ID
   */
  async getSyncLog(id: number): Promise<SyncLogDetailResponse> {
    const response = await api.get(`/sync-logs/${id}`);
    return response.data;
  }

  /**
   * Delete sync log
   */
  async deleteSyncLog(id: number) {
    const response = await api.delete(`/sync-logs/${id}`);
    return response.data;
  }

  /**
   * Get sync log statistics
   */
  async getSyncLogStats(filters?: {
    source_id?: number;
    sync_type?: string;
  }): Promise<SyncLogStatsResponse> {
    const response = await api.get('/sync-logs/stats', {
      params: filters,
    });
    return response.data;
  }
}

export const SYNC_TYPES = [
  { id: 'live_categories', label: 'Live Categories' },
  { id: 'live_streams', label: 'Live Streams' },
  { id: 'vod_categories', label: 'VOD Categories' },
  { id: 'vod_streams', label: 'VOD Streams' },
  { id: 'series_categories', label: 'Series Categories' },
  { id: 'series', label: 'Series' },
] as const;

export default new SyncLogsApi();
