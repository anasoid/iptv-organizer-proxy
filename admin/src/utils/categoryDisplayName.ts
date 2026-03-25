import type { Category } from '../services/categoriesApi';

export function getCategoryDisplayName(category: Category): string {
  return category.name || (category as Category & { category_name?: string }).category_name || 'Unnamed Category';
}

