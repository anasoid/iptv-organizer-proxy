import api from './api';
import type { Stream } from '../types';

export interface StreamsListResponse {
  success: boolean;
  data: Stream[];
  pagination: {
    page: number;
    limit: number;
    total: number;
    pages: number;
  };
}

export interface StreamResponse {
  success: boolean;
  data: Stream;
}

type StreamType = 'live' | 'vod' | 'series';

class StreamsApi {
  /**
   * Get all streams by source and type (paginated)
   * Optional filter by categoryId, search by name, and streamId
   */
  async getStreams(
    sourceId: number,
    type: StreamType,
    categoryId?: number,
    page: number = 1,
    limit: number = 20,
    search?: string,
    streamId?: number | string
  ) {
    const params: Record<string, string | number | boolean> = { sourceId, type, page, limit };
    if (categoryId) {
      params.categoryId = categoryId;
    }
    if (search) {
      params.search = search;
    }
    if (streamId !== undefined && streamId !== '') {
      params.streamId = streamId;
    }

    const response = await api.get('/streams', { params });
    return response.data as StreamsListResponse;
  }

  /**
   * Get single stream by ID and type
   */
  async getStream(id: number, type?: StreamType) {
    const params: Record<string, string> = {};
    if (type) {
      params.type = type;
    }
    const response = await api.get(`/streams/${id}`, { params });
    return response.data as StreamResponse;
  }

  /**
   * Get live streams
   */
  async getLiveStreams(sourceId: number, categoryId?: number, page: number = 1, limit: number = 20, search?: string, streamId?: number | string) {
    return this.getStreams(sourceId, 'live', categoryId, page, limit, search, streamId);
  }

  /**
   * Get VOD streams
   */
  async getVodStreams(sourceId: number, categoryId?: number, page: number = 1, limit: number = 20, search?: string, streamId?: number | string) {
    return this.getStreams(sourceId, 'vod', categoryId, page, limit, search, streamId);
  }

  /**
   * Get series streams
   */
  async getSeriesStreams(sourceId: number, categoryId?: number, page: number = 1, limit: number = 20, search?: string, streamId?: number | string) {
    return this.getStreams(sourceId, 'series', categoryId, page, limit, search, streamId);
  }

  /**
   * Update stream allow_deny field
   * @param id - stream database ID
   * @param allowDeny - 'allow', 'deny', or null to remove override
   * @param type - stream type (live, vod, series)
   */
  async updateAllowDeny(id: number, allowDeny: 'allow' | 'deny' | null, type?: StreamType) {
    const params: Record<string, string> = {};
    if (type) {
      params.type = type;
    }
    const response = await api.patch(`/streams/${id}/allow-deny`, { allowDeny }, { params });
    return response.data as StreamResponse;
  }
}

export default new StreamsApi();
