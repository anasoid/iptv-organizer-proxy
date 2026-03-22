import api from './api';
import type { Category } from '../types';

export type CategoryTypeFilter = 'live' | 'vod' | 'series';
export type CategoryAllowDenyFilter = 'all' | 'allow' | 'deny' | 'default';
export type CategoryBlackListFilter = 'all' | 'default' | 'hidden' | 'visible' | 'force_hidden';

export interface CategoriesFilters {
  allowDenyFilter?: CategoryAllowDenyFilter;
  blackListFilter?: CategoryBlackListFilter;
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
   * Optional search by name and server-side filters by category type, access control, and blacklist
   */
  async getCategories(
    sourceId: number,
    page: number = 1,
    limit: number = 20,
    search?: string,
    categoryType?: CategoryTypeFilter,
    filters?: CategoriesFilters,
  ) {
    const params: Record<string, string | number> = { sourceId, page, limit };
    if (search) {
      params.search = search;
    }
    if (categoryType) {
      params.categoryType = categoryType;
    }
    if (filters?.allowDenyFilter && filters.allowDenyFilter !== 'all') {
      params.allowDenyFilter = filters.allowDenyFilter;
    }
    if (filters?.blackListFilter && filters.blackListFilter !== 'all') {
      params.blackListFilter = filters.blackListFilter;
    }
    const response = await api.get('/categories', { params });
    return response.data as CategoriesListResponse;
  }

  /**
   * Get single category by ID
   * @param id - category ID
   * @param sourceId - if provided, searches by source_id
   */
  async getCategory(id: number, sourceId?: number) {
    const params = sourceId ? { sourceId } : {};
    const response = await api.get(`/categories/${id}`, { params });
    return response.data as CategoryResponse;
  }

  /**
   * Update category allow_deny field
   * @param id - category database ID
   * @param allowDeny - 'allow', 'deny', or null to remove override
   */
  async updateAllowDeny(id: number, allowDeny: 'allow' | 'deny' | null) {
    const response = await api.patch(`/categories/${id}/allow-deny`, { allowDeny });
    return response.data as CategoryResponse;
  }

  /**
   * Update category blacklist status
   * @param id - category database ID
   * @param blackList - 'hide', 'visible', 'force_hide', 'force_visible', or 'default'
   */
  async updateBlackList(id: number, blackList: 'default' | 'hide' | 'visible' | 'force_hide' | 'force_visible') {
    const response = await api.patch(`/categories/${id}/blacklist`, { blackList });
    return response.data as CategoryResponse;
  }
}

export default new CategoriesApi();
