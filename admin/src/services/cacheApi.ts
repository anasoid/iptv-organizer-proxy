import api from './api';

export interface CacheStat {
  cacheName: string;
  hits: number;
  misses: number;
  puts: number;
  sizeEvictions: number;
  expiredEvictions: number;
  invalidations: number;
  clears: number;
  size: number;
  maxSize: number;
  hitRate?: number;
  totalEvictions?: number;
}

export interface DatabaseShrinkResult {
  dialect: string;
  databasePath: string;
  sizeBeforeBytes: number;
  sizeAfterBytes: number;
  freedBytes: number;
  durationMs: number;
}

class CacheApi {
  /**
   * Get all cache statistics
   */
  async getCacheStats() {
    const response = await api.get<{ data: CacheStat[] }>('/cache/stats');
    return response.data.data;
  }

  /**
   * Trigger SQLite VACUUM to compact database file and free disk space.
   */
  async shrinkDatabase() {
    const response = await api.post<{ data: DatabaseShrinkResult }>('/cache/database/shrink');
    return response.data.data;
  }
}

export const cacheApi = new CacheApi();
export default cacheApi;
