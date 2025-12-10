import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Box,
  Card,
  CardContent,
  CircularProgress,
  Grid,
  Pagination,
  Typography,
  Chip,
  Alert,
  TextField,
} from '@mui/material';
import { DataGrid, type GridColDef } from '@mui/x-data-grid';
import { useQuery } from '@tanstack/react-query';
import { useAuthStore } from '../stores/authStore';
import { useSourceStore } from '../stores/sourceStore';
import categoriesApi, { type Category } from '../services/categoriesApi';
import SourceSelector from '../components/SourceSelector';
import ViewToggle, { type ViewMode } from '../components/ViewToggle';

export default function Categories() {
  const navigate = useNavigate();
  const { isAuthenticated } = useAuthStore();
  const sourceId = useSourceStore((state) => state.selectedSourceId);
  const setSourceId = useSourceStore((state) => state.setSelectedSourceId);
  const [page, setPage] = useState(1);
  const [limit] = useState(20);
  const [view, setView] = useState<ViewMode>('list');
  const [searchQuery, setSearchQuery] = useState('');

  // Fetch categories with optional search
  const { data, isLoading, error } = useQuery({
    queryKey: ['categories', sourceId, page, limit, searchQuery],
    queryFn: () => (sourceId ? categoriesApi.getCategories(sourceId, page, limit, searchQuery || undefined) : Promise.resolve(null)),
    enabled: isAuthenticated && sourceId !== null,
  });

  const categories = data?.data || [];
  const pagination = data?.pagination;

  const getCategoryTypeColor = (type: string): 'default' | 'primary' | 'secondary' | 'error' | 'warning' | 'info' | 'success' => {
    switch (type) {
      case 'live':
        return 'primary';
      case 'vod':
        return 'secondary';
      case 'series':
        return 'warning';
      default:
        return 'default';
    }
  };

  const columns: GridColDef[] = [
    { field: 'id', headerName: 'ID', width: 70 },
    { field: 'category_name', headerName: 'Name', width: 200, flex: 1 },
    {
      field: 'category_type',
      headerName: 'Type',
      width: 100,
      renderCell: (params) => (
        <Chip
          label={params.value?.toUpperCase()}
          color={getCategoryTypeColor(params.value)}
          size="small"
        />
      ),
    },
    { field: 'category_id', headerName: 'Category ID', width: 120 },
    { field: 'labels', headerName: 'Labels', width: 200, flex: 1 },
  ];

  const CategoryGridCard = ({ category }: { category: Category }) => (
    <Card
      onClick={() => navigate(`/categories/${category.id}`)}
      sx={{
        height: '100%',
        display: 'flex',
        flexDirection: 'column',
        cursor: 'pointer',
        transition: 'transform 0.2s, box-shadow 0.2s',
        '&:hover': {
          transform: 'translateY(-4px)',
          boxShadow: (theme) => theme.shadows[8],
        },
      }}
    >
      <CardContent sx={{ flexGrow: 1 }}>
        <Typography variant="subtitle2" component="div" sx={{ fontWeight: 600, mb: 1, minHeight: '2.4em' }}>
          {category.category_name}
        </Typography>

        <Typography variant="caption" sx={{ color: 'text.secondary', display: 'block', mb: 1 }}>
          ID: {category.category_id}
        </Typography>

        <Box sx={{ display: 'flex', gap: 0.5, flexWrap: 'wrap', mt: 2 }}>
          <Chip
            label={category.category_type.toUpperCase()}
            color={getCategoryTypeColor(category.category_type)}
            size="small"
          />
          {category.labels && (
            <Chip
              label={`${category.labels.split(',').length} labels`}
              size="small"
              variant="outlined"
            />
          )}
        </Box>
      </CardContent>
    </Card>
  );

  return (
    <Box sx={{ p: 3 }}>
      <Typography variant="h4" sx={{ mb: 3 }}>
        Categories
      </Typography>

      {/* Source Selector and Search Bar */}
      <Card sx={{ mb: 3, p: 2 }}>
        <Box sx={{ display: 'flex', gap: 2, alignItems: 'flex-end' }}>
          <Box sx={{ flex: '0 0 auto', minWidth: 300 }}>
            <Typography variant="subtitle2" sx={{ mb: 2, fontWeight: 600 }}>
              Filter by Source
            </Typography>
            <SourceSelector
              sourceId={sourceId}
              onChange={(id) => {
                setSourceId(id);
                setPage(1);
              }}
              required
            />
          </Box>

          {sourceId && (
            <TextField
              placeholder="Search by category name..."
              value={searchQuery}
              onChange={(e) => {
                setSearchQuery(e.target.value);
                setPage(1);
              }}
              size="small"
              variant="outlined"
              sx={{ flex: '1 1 auto', minWidth: 200 }}
            />
          )}
        </Box>
      </Card>

      {/* View Controls */}
      {sourceId && (
        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
          <Typography variant="body2" sx={{ color: 'text.secondary' }}>
            {pagination?.total || 0} categories
          </Typography>
          <ViewToggle view={view} onChange={setView} />
        </Box>
      )}

      {/* Error State */}
      {error && (
        <Alert severity="error" sx={{ mb: 2 }}>
          Failed to load categories. Please try again.
        </Alert>
      )}

      {/* Loading State */}
      {isLoading && sourceId && (
        <Box sx={{ display: 'flex', justifyContent: 'center', p: 4 }}>
          <CircularProgress />
        </Box>
      )}

      {/* Empty State */}
      {!isLoading && sourceId && categories.length === 0 && (
        <Alert severity="info">
          {searchQuery ? 'No categories match your search.' : 'No categories found for the selected source.'}
        </Alert>
      )}

      {/* List View */}
      {!isLoading && sourceId && view === 'list' && categories.length > 0 && (
        <>
          <Box sx={{ height: 500, width: '100%' }}>
            <DataGrid
              rows={categories}
              columns={columns}
              pageSizeOptions={[10, 20, 50]}
              disableSelectionOnClick
              onRowClick={(params) => navigate(`/categories/${params.row.id}`)}
              sx={{
                '& .MuiDataGrid-cell': { py: 1 },
                '& .MuiDataGrid-row': { cursor: 'pointer' },
              }}
              initialState={{
                pagination: { paginationModel: { pageSize: limit } },
              }}
            />
          </Box>
          {pagination && pagination.pages > 1 && (
            <Box sx={{ display: 'flex', justifyContent: 'center', mt: 2 }}>
              <Pagination
                count={pagination.pages}
                page={page}
                onChange={(_, value) => setPage(value)}
                color="primary"
              />
            </Box>
          )}
        </>
      )}

      {/* Grid View */}
      {!isLoading && sourceId && view === 'grid' && categories.length > 0 && (
        <>
          <Grid container spacing={2}>
            {categories.map((category) => (
              <Grid item xs={12} sm={6} md={4} lg={3} key={category.id}>
                <CategoryGridCard category={category} />
              </Grid>
            ))}
          </Grid>
          {pagination && pagination.pages > 1 && (
            <Box sx={{ display: 'flex', justifyContent: 'center', mt: 3 }}>
              <Pagination
                count={pagination.pages}
                page={page}
                onChange={(_, value) => setPage(value)}
                color="primary"
              />
            </Box>
          )}
        </>
      )}
    </Box>
  );
}
