import api from './api';

export interface StreamData {
  [key: string]: any;
  stream_icon?: string;
}

export interface Stream {
  id: number;
  source_id: number;
  stream_id: string | number;
  name: string;
  category_id: string | number | null;
  category_ids?: string | number[] | null;
  is_adult: number;
  labels?: string | null;
  data?: StreamData | null;
  created_at: string;
  updated_at: string;
}

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
   * Optional filter by category_id
   */
  async getStreams(
    sourceId: number,
    type: StreamType,
    categoryId?: number,
    page: number = 1,
    limit: number = 20
  ) {
    const params: any = { source_id: sourceId, type, page, limit };
    if (categoryId) {
      params.category_id = categoryId;
    }

    const response = await api.get('/streams', { params });
    return response.data as StreamsListResponse;
  }

  /**
   * Get single stream by ID
   */
  async getStream(id: number) {
    const response = await api.get(`/streams/${id}`);
    return response.data as StreamResponse;
  }

  /**
   * Get live streams
   */
  async getLiveStreams(sourceId: number, categoryId?: number, page: number = 1, limit: number = 20) {
    return this.getStreams(sourceId, 'live', categoryId, page, limit);
  }

  /**
   * Get VOD streams
   */
  async getVodStreams(sourceId: number, categoryId?: number, page: number = 1, limit: number = 20) {
    return this.getStreams(sourceId, 'vod', categoryId, page, limit);
  }

  /**
   * Get series streams
   */
  async getSeriesStreams(sourceId: number, categoryId?: number, page: number = 1, limit: number = 20) {
    return this.getStreams(sourceId, 'series', categoryId, page, limit);
  }
}

export default new StreamsApi();
