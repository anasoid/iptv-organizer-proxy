import api from './api';

export interface AccessControlExportData {
  exported_at: string;
  version: string;
  source_id?: number | null;
  streams: Array<{
    id: number;
    source_id: number;
    stream_id: string | number;
    name: string;
    type: 'live' | 'vod' | 'series';
    allow_deny: 'allow' | 'deny';
  }>;
  categories: Array<{
    id: number;
    source_id: number;
    category_id: string | number;
    category_name: string;
    category_type: 'live' | 'vod' | 'series';
    allow_deny: 'allow' | 'deny';
  }>;
}

export interface AccessControlImportResponse {
  success: boolean;
  message: string;
  data: {
    streams_updated: number;
    streams_failed: number;
    categories_updated: number;
    categories_failed: number;
    errors: string[];
  };
}

class AccessControlApi {
  /**
   * Export access control settings
   * @param sourceId - Optional source ID to filter exports
   * @param format - Export format: 'json' or 'yaml'
   */
  async export(sourceId?: number, format: 'json' | 'yaml' = 'json'): Promise<Blob> {
    const params: Record<string, string | number> = { format };
    if (sourceId) {
      params.source_id = sourceId;
    }
    const response = await api.get('/access-control/export', { params, responseType: 'blob' });
    return response.data;
  }

  /**
   * Import access control settings
   * @param data - Import data containing streams and categories access control settings
   */
  async import(data: AccessControlExportData): Promise<AccessControlImportResponse> {
    const response = await api.post('/access-control/import', data);
    return response.data;
  }
}

export default new AccessControlApi();
