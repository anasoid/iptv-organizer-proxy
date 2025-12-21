import { useParams, useNavigate } from 'react-router-dom';
import {
  Box,
  Card,
  CircularProgress,
  Grid,
  Typography,
  Chip,
  Alert,
  Button,
  Divider,
} from '@mui/material';
import { DataGrid, type GridColDef } from '@mui/x-data-grid';
import { ArrowBack as ArrowBackIcon } from '@mui/icons-material';
import { useQuery } from '@tanstack/react-query';
import { useAuthStore } from '../stores/authStore';
import categoriesApi from '../services/categoriesApi';
import streamsApi from '../services/streamsApi';

export default function CategoryDetail() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { isAuthenticated } = useAuthStore();

  const categoryId = id ? parseInt(id, 10) : null;

  // Fetch category details
  const { data: categoryData, isLoading: isLoadingCategory, error: categoryError } = useQuery({
    queryKey: ['category', categoryId],
    queryFn: () => (categoryId ? categoriesApi.getCategory(categoryId) : Promise.resolve(null)),
    enabled: isAuthenticated && categoryId !== null,
  });

  const category = categoryData?.data;

  // Fetch streams only for the same type as the category
  const { data: streamsData, isLoading: isLoadingStreams } = useQuery({
    queryKey: ['category-streams', category?.source_id, category?.category_id, category?.category_type],
    queryFn: () => {
      if (!category) return Promise.resolve(null);
      const categoryId = typeof category.category_id === 'string' ? parseInt(category.category_id, 10) : category.category_id;
      switch (category.category_type) {
        case 'live':
          return streamsApi.getLiveStreams(category.source_id, categoryId);
        case 'vod':
          return streamsApi.getVodStreams(category.source_id, categoryId);
        case 'series':
          return streamsApi.getSeriesStreams(category.source_id, categoryId);
        default:
          return Promise.resolve(null);
      }
    },
    enabled: isAuthenticated && !!category,
  });

  const streams = streamsData?.data || [];

  const getCategoryTypeColor = (
    type: string
  ): 'default' | 'primary' | 'secondary' | 'error' | 'warning' | 'info' | 'success' => {
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

  const streamColumns: GridColDef[] = [
    { field: 'id', headerName: 'ID', width: 70 },
    { field: 'stream_id', headerName: 'Stream ID', width: 100 },
    { field: 'name', headerName: 'Name', width: 200, flex: 1 },
    {
      field: 'is_adult',
      headerName: 'Adult',
      width: 80,
      renderCell: (params) => (params.value ? <Chip label="Adult" color="error" size="small" /> : '—'),
    },
  ];

  if (isLoadingCategory) {
    return (
      <Box sx={{ p: 3, display: 'flex', justifyContent: 'center' }}>
        <CircularProgress />
      </Box>
    );
  }

  if (categoryError || !category) {
    return (
      <Box sx={{ p: 3 }}>
        <Button
          startIcon={<ArrowBackIcon />}
          onClick={() => navigate('/categories')}
          sx={{ mb: 2 }}
        >
          Back to Categories
        </Button>
        <Alert severity="error">Failed to load category details.</Alert>
      </Box>
    );
  }

  return (
    <Box sx={{ p: 3 }}>
      {/* Header */}
      <Button
        startIcon={<ArrowBackIcon />}
        onClick={() => navigate('/categories')}
        sx={{ mb: 2 }}
      >
        Back to Categories
      </Button>

      {/* Category Info Card */}
      <Card sx={{ mb: 3, p: 3 }}>
        <Grid container spacing={2} alignItems="center">
          <Grid item xs={12} sm="auto">
            <Chip
              label={category.category_type.toUpperCase()}
              color={getCategoryTypeColor(category.category_type)}
              size="medium"
            />
          </Grid>
        </Grid>

        <Typography variant="h4" sx={{ mt: 2, mb: 1 }}>
          {category.category_name}
        </Typography>

        <Divider sx={{ my: 2 }} />

        <Grid container spacing={3}>
          <Grid item xs={12} sm={6}>
            <Typography variant="subtitle2" sx={{ fontWeight: 600, color: 'text.secondary' }}>
              Category ID
            </Typography>
            <Typography variant="body1">{category.category_id}</Typography>
          </Grid>

          <Grid item xs={12} sm={6}>
            <Typography variant="subtitle2" sx={{ fontWeight: 600, color: 'text.secondary' }}>
              Type
            </Typography>
            <Typography variant="body1" sx={{ textTransform: 'capitalize' }}>
              {category.category_type}
            </Typography>
          </Grid>

          <Grid item xs={12} sm={6}>
            <Typography variant="subtitle2" sx={{ fontWeight: 600, color: 'text.secondary' }}>
              Order Number
            </Typography>
            <Chip label={`#${category.num}`} color="primary" variant="outlined" />
          </Grid>

          {category.parent_id && (
            <Grid item xs={12} sm={6}>
              <Typography variant="subtitle2" sx={{ fontWeight: 600, color: 'text.secondary' }}>
                Parent Category ID
              </Typography>
              <Typography variant="body1">{category.parent_id}</Typography>
            </Grid>
          )}

          {category.labels && (
            <Grid item xs={12} sm={6}>
              <Typography variant="subtitle2" sx={{ fontWeight: 600, color: 'text.secondary' }}>
                Labels
              </Typography>
              <Box sx={{ display: 'flex', gap: 0.5, flexWrap: 'wrap', mt: 0.5 }}>
                {category.labels.split(',').map((label, idx) => (
                  <Chip key={idx} label={label.trim()} size="small" variant="outlined" />
                ))}
              </Box>
            </Grid>
          )}

          <Grid item xs={12} sm={6}>
            <Typography variant="subtitle2" sx={{ fontWeight: 600, color: 'text.secondary' }}>
              Created At
            </Typography>
            <Typography variant="body1">
              {new Date(category.created_at).toLocaleDateString('en-US', {
                year: 'numeric',
                month: 'short',
                day: 'numeric',
                hour: '2-digit',
                minute: '2-digit',
              })}
            </Typography>
          </Grid>
        </Grid>
      </Card>

      {/* Streams for this category type */}
      <Card>
        <Box sx={{ p: 3 }}>
          <Typography variant="h6" sx={{ mb: 2 }}>
            {category.category_type === 'live'
              ? 'Live Streams'
              : category.category_type === 'vod'
                ? 'VOD Streams'
                : 'Series'}{' '}
            ({streamsData?.pagination?.total || 0})
          </Typography>

          {isLoadingStreams && (
            <Box sx={{ display: 'flex', justifyContent: 'center', py: 3 }}>
              <CircularProgress />
            </Box>
          )}

          {!isLoadingStreams && streams.length === 0 && (
            <Alert severity="info">
              No {category.category_type === 'live' ? 'live streams' : category.category_type === 'vod' ? 'VOD streams' : 'series'} in this category.
            </Alert>
          )}

          {!isLoadingStreams && streams.length > 0 && (
            <Box sx={{ height: 500, width: '100%' }}>
              <DataGrid
                rows={streams}
                columns={streamColumns}
                pageSizeOptions={[10, 20, 50]}
                disableSelectionOnClick
                onRowClick={(params) => {
                  sessionStorage.setItem('streamDetailReferrer', window.location.pathname);
                  navigate(`/streams/${params.row.id}/${category.category_type}`);
                }}
                sx={{
                  '& .MuiDataGrid-cell': { py: 1 },
                  '& .MuiDataGrid-row': { cursor: 'pointer' },
                }}
                initialState={{
                  pagination: { paginationModel: { pageSize: 10 } },
                }}
              />
            </Box>
          )}
        </Box>
      </Card>
    </Box>
  );
}
