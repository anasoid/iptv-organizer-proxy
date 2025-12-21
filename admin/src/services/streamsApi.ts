import api from './api';

export interface StreamData {
  [key: string]: unknown;
  stream_icon?: string;
}

export interface Stream {
  id: number;
  source_id: number;
  stream_id: string | number;
  num: number;
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

interface StreamsParams {
  source_id: number;
  type: StreamType;
  page: number;
  limit: number;
  category_id?: number;
  search?: string;
  stream_id?: number | string;
}

class StreamsApi {
  /**
   * Get all streams by source and type (paginated)
   * Optional filter by category_id, search by name, and stream_id
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
    const params: StreamsParams = { source_id: sourceId, type, page, limit };
    if (categoryId) {
      params.category_id = categoryId;
    }
    if (search) {
      params.search = search;
    }
    if (streamId !== undefined && streamId !== '') {
      params.stream_id = streamId;
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
}

export default new StreamsApi();
