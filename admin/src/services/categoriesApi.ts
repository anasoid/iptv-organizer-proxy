import api from './api';

export interface Category {
  id: number;
  source_id: number;
  category_id: string | number;
  category_name: string;
  category_type: 'live' | 'vod' | 'series';
  num: number;
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
   * Optional search by name and filter by category type
   */
  async getCategories(sourceId: number, page: number = 1, limit: number = 20, search?: string, categoryType?: 'live' | 'vod' | 'series') {
    const params: { source_id: number; page: number; limit: number; search?: string; category_type?: string } = { source_id: sourceId, page, limit };
    if (search) {
      params.search = search;
    }
    if (categoryType) {
      params.category_type = categoryType;
    }
    const response = await api.get('/categories', { params });
    return response.data as CategoriesListResponse;
  }

  /**
   * Get single category by ID
   * @param id - category ID (can be database id or functional category_id depending on lookup)
   * @param sourceId - if provided, searches by source_id + category_id (functional lookup)
   */
  async getCategory(id: number, sourceId?: number) {
    const params = sourceId ? { source_id: sourceId } : {};
    const response = await api.get(`/categories/${id}`, { params });
    return response.data as CategoryResponse;
  }
}

export default new CategoriesApi();
