import { api } from './api';

export interface Filter {
  id: number;
  name: string;
  description?: string;
  filter_config: string;
  use_source_filter?: boolean | number;
  favoris?: string | null;
  created_at?: string;
  updated_at?: string;
}

export interface FilterResponse {
  success: boolean;
  data?: Filter | Filter[];
  message?: string;
  pagination?: {
    page: number;
    limit: number;
    total: number;
    pages: number;
  };
}

const filtersApi = {
  async getFilters(page: number = 1, limit: number = 10): Promise<FilterResponse> {
    const response = await api.get('/filters', {
      params: { page, limit },
    });
    return response.data;
  },

  async getFilter(id: number): Promise<FilterResponse> {
    const response = await api.get(`/filters/${id}`);
    return response.data;
  },

  async createFilter(data: {
    name: string;
    description?: string;
    filter_config: string;
    use_source_filter?: boolean | number;
  }): Promise<FilterResponse> {
    const response = await api.post('/filters', data);
    return response.data;
  },

  async updateFilter(
    id: number,
    data: {
      name?: string;
      description?: string;
      filter_config?: string;
      use_source_filter?: boolean | number;
    }
  ): Promise<FilterResponse> {
    const response = await api.put(`/filters/${id}`, data);
    return response.data;
  },

  async deleteFilter(id: number): Promise<FilterResponse> {
    const response = await api.delete(`/filters/${id}`);
    return response.data;
  },

  async updateUseSourceFilter(id: number, useSourceFilter: boolean): Promise<FilterResponse> {
    const response = await api.patch(`/filters/${id}/use-source-filter`, { use_source_filter: useSourceFilter });
    return response.data;
  },
};

export default filtersApi;
