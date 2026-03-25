import { useMemo, useState } from 'react';
import {
  Box,
  Card,
  Chip,
  FormControl,
  InputLabel,
  MenuItem,
  Select,
  TextField,
  Typography,
} from '@mui/material';
import type { Category } from '../services/categoriesApi';
import { getCategoryDisplayName } from '../utils/categoryDisplayName';

export interface CategoryFilterSidebarProps {
  categories: Category[];
  selectedCategoryId: number | null;
  onCategorySelect: (categoryId: number | null) => void;
  title?: string;
}

type SidebarBlackListFilter = 'all' | 'blacklisted' | 'not_blacklisted';

function isBlacklistedCategory(category: Category): boolean {
  const value = (
    category.blackList
    ?? (category as Category & { black_list?: string | null }).black_list
    ?? 'default'
  ).toLowerCase();
  return value === 'hide' || value === 'force_hide';
}

export default function CategoryFilterSidebar({
  categories,
  selectedCategoryId,
  onCategorySelect,
  title = 'Filter by Category',
}: CategoryFilterSidebarProps) {
  const [nameFilter, setNameFilter] = useState('');
  const [blackListFilter, setBlackListFilter] = useState<SidebarBlackListFilter>('all');

  const filteredCategories = useMemo(() => {
    const normalizedNameFilter = nameFilter.trim().toLowerCase();
    return categories.filter((category) => {
      const displayName = getCategoryDisplayName(category).toLowerCase();
      const nameMatches = normalizedNameFilter.length === 0 || displayName.includes(normalizedNameFilter);
      const blacklisted = isBlacklistedCategory(category);

      const blackListMatches =
        blackListFilter === 'all'
          ? true
          : blackListFilter === 'blacklisted'
            ? blacklisted
            : !blacklisted;

      return nameMatches && blackListMatches;
    });
  }, [categories, nameFilter, blackListFilter]);

  return (
    <Box sx={{ flex: '0 0 calc(25% - 24px)' }}>
      <Card sx={{ position: 'sticky', top: 20, width: '100%', minHeight: 200, backgroundColor: '#fafafa' }}>
        <Box sx={{ p: 2, backgroundColor: 'background.paper' }}>
          <Typography variant="subtitle2" sx={{ fontWeight: 600, mb: 2 }}>
            {title} ({filteredCategories.length}/{categories.length})
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
          {filteredCategories.map((category) => (
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
          {filteredCategories.length === 0 && (
            <Typography variant="body2" sx={{ p: 2, color: 'text.secondary' }}>
              No categories match the current filters.
            </Typography>
          )}
        </Box>

        <Box sx={{ p: 2, borderTop: '1px solid #e0e0e0', backgroundColor: 'background.paper' }}>
          <TextField
            size="small"
            label="Search category"
            value={nameFilter}
            onChange={(e) => setNameFilter(e.target.value)}
            fullWidth
            sx={{ mb: 1 }}
          />
          <FormControl size="small" fullWidth>
            <InputLabel>Blacklist</InputLabel>
            <Select
              value={blackListFilter}
              label="Blacklist"
              onChange={(e) => setBlackListFilter(e.target.value as SidebarBlackListFilter)}
            >
              <MenuItem value="all">All</MenuItem>
              <MenuItem value="blacklisted">Blacklisted</MenuItem>
              <MenuItem value="not_blacklisted">Not Blacklisted</MenuItem>
            </Select>
          </FormControl>
        </Box>
      </Card>
    </Box>
  );
}



