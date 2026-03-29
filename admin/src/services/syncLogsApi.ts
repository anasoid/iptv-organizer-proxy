import api from './api';

export interface SyncLog {
  id: number;
  sourceId: number;
  sourceName?: string;
  syncType: string;
  startedAt: string;
  completedAt?: string | null;
  durationSeconds?: number | null;
  status: 'running' | 'completed' | 'failed';
  itemsAdded: number;
  itemsUpdated: number;
  itemsDeleted: number;
  errorMessage?: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface SyncLogStats {
  totalSyncs: number;
  completedSyncs: number;
  failedSyncs: number;
  runningSyncs?: number;
  totalAdded: number;
  totalUpdated: number;
  totalDeleted: number;
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
      sourceId?: number;
      syncType?: string;
      status?: string;
    },
    sortBy?: string,
    sortOrder?: 'asc' | 'desc'
  ): Promise<SyncLogsResponse> {
    const params: any = { page, limit, ...filters };
    if (sortBy) {
      params.sortBy = sortBy;
    }
    if (sortOrder) {
      params.sortOrder = sortOrder;
    }
    const response = await api.get('/sync-logs', {
      params,
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
    sourceId?: number;
    syncType?: string;
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
