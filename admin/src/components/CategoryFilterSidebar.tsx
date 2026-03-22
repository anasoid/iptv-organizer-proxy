import { Box, Card, Chip, Typography } from '@mui/material';
import type { Category } from '../services/categoriesApi';
import { getCategoryDisplayName } from '../utils/categoryDisplayName';

export interface CategoryFilterSidebarProps {
  categories: Category[];
  selectedCategoryId: number | null;
  onCategorySelect: (categoryId: number | null) => void;
  title?: string;
}

export default function CategoryFilterSidebar({
  categories,
  selectedCategoryId,
  onCategorySelect,
  title = 'Filter by Category',
}: CategoryFilterSidebarProps) {
  return (
    <Box sx={{ flex: '0 0 calc(25% - 24px)' }}>
      <Card sx={{ position: 'sticky', top: 20, width: '100%', minHeight: 200, backgroundColor: '#fafafa' }}>
        <Box sx={{ p: 2, backgroundColor: 'background.paper' }}>
          <Typography variant="subtitle2" sx={{ fontWeight: 600, mb: 2 }}>
            {title} ({categories.length})
          </Typography>
          <Chip
            label="All Categories"
            onClick={() => onCategorySelect(null)}
            variant={selectedCategoryId === null ? 'filled' : 'outlined'}
            color={selectedCategoryId === null ? 'primary' : 'default'}
            size="small"
            sx={{ width: '100%', mb: 1 }}
          />
        </Box>

        <Box sx={{ maxHeight: 400, overflow: 'auto', borderTop: '1px solid #e0e0e0' }}>
          {categories.map((category) => (
            <Box
              key={category.id}
              onClick={() => onCategorySelect(Number(category.externalId))}
              sx={{
                p: 1.5,
                px: 2,
                cursor: 'pointer',
                backgroundColor:
                  selectedCategoryId === Number(category.externalId) ? 'primary.light' : 'transparent',
                '&:hover': {
                  backgroundColor:
                    selectedCategoryId === Number(category.externalId)
                      ? 'primary.light'
                      : 'action.hover',
                },
                borderBottom: '1px solid #f0f0f0',
              }}
            >
              <Typography variant="body2" sx={{ fontWeight: 500 }}>
                {getCategoryDisplayName(category)}
              </Typography>
            </Box>
          ))}
        </Box>
      </Card>
    </Box>
  );
}


