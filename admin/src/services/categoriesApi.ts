import api from './api';

export interface Category {
  id: number;
  source_id: number;
  category_id: string | number;
  category_name: string;
  category_type: 'live' | 'vod' | 'series';
  parent_id?: number | null;
  labels?: string | null;
  created_at: string;
}

export interface CategoriesListResponse {
  success: boolean;
  data: Category[];
  pagination: {
    page: number;
    limit: number;
    total: number;
    pages: number;
  };
}

export interface CategoryResponse {
  success: boolean;
  data: Category;
}

class CategoriesApi {
  /**
   * Get all categories by source (paginated)
   */
  async getCategories(sourceId: number, page: number = 1, limit: number = 20) {
    const response = await api.get('/categories', {
      params: { source_id: sourceId, page, limit },
    });
    return response.data as CategoriesListResponse;
  }

  /**
   * Get single category by ID
   */
  async getCategory(id: number) {
    const response = await api.get(`/categories/${id}`);
    return response.data as CategoryResponse;
  }
}

export default new CategoriesApi();
