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
  Tab,
  Tabs,
} from '@mui/material';
import { DataGrid, type GridColDef } from '@mui/x-data-grid';
import { ArrowBack as ArrowBackIcon } from '@mui/icons-material';
import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { useAuthStore } from '../stores/authStore';
import categoriesApi from '../services/categoriesApi';
import streamsApi from '../services/streamsApi';

export default function CategoryDetail() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { isAuthenticated } = useAuthStore();
  const [tabValue, setTabValue] = useState(0);

  const categoryId = id ? parseInt(id, 10) : null;

  // Fetch category details
  const { data: categoryData, isLoading: isLoadingCategory, error: categoryError } = useQuery({
    queryKey: ['category', categoryId],
    queryFn: () => (categoryId ? categoriesApi.getCategory(categoryId) : Promise.resolve(null)),
    enabled: isAuthenticated && categoryId !== null,
  });

  const category = categoryData?.data;

  // Fetch related streams (all types)
  const { data: liveStreamsData } = useQuery({
    queryKey: ['category-live-streams', category?.source_id, category?.id],
    queryFn: () =>
      category
        ? streamsApi.getLiveStreams(category.source_id, category.id)
        : Promise.resolve(null),
    enabled: isAuthenticated && !!category && tabValue === 0,
  });

  const { data: vodStreamsData } = useQuery({
    queryKey: ['category-vod-streams', category?.source_id, category?.id],
    queryFn: () =>
      category
        ? streamsApi.getVodStreams(category.source_id, category.id)
        : Promise.resolve(null),
    enabled: isAuthenticated && !!category && tabValue === 1,
  });

  const { data: seriesStreamsData } = useQuery({
    queryKey: ['category-series-streams', category?.source_id, category?.id],
    queryFn: () =>
      category
        ? streamsApi.getSeriesStreams(category.source_id, category.id)
        : Promise.resolve(null),
    enabled: isAuthenticated && !!category && tabValue === 2,
  });

  const liveStreams = liveStreamsData?.data || [];
  const vodStreams = vodStreamsData?.data || [];
  const seriesStreams = seriesStreamsData?.data || [];

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

      {/* Streams by Type */}
      <Card>
        <Box sx={{ borderBottom: 1, borderColor: 'divider' }}>
          <Tabs value={tabValue} onChange={(_, value) => setTabValue(value)}>
            <Tab label={`Live Streams (${liveStreamsData?.pagination?.total || 0})`} />
            <Tab label={`VOD Streams (${vodStreamsData?.pagination?.total || 0})`} />
            <Tab label={`Series (${seriesStreamsData?.pagination?.total || 0})`} />
          </Tabs>
        </Box>

        <Box sx={{ p: 2 }}>
          {/* Live Streams Tab */}
          {tabValue === 0 && (
            <>
              {liveStreams.length === 0 ? (
                <Alert severity="info">No live streams in this category.</Alert>
              ) : (
                <Box sx={{ height: 400, width: '100%' }}>
                  <DataGrid
                    rows={liveStreams}
                    columns={streamColumns}
                    pageSizeOptions={[10, 20, 50]}
                    disableSelectionOnClick
                    onRowClick={(params) => {
                      sessionStorage.setItem('streamDetailReferrer', window.location.pathname);
                      navigate(`/streams/${params.row.id}/live`);
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
            </>
          )}

          {/* VOD Streams Tab */}
          {tabValue === 1 && (
            <>
              {vodStreams.length === 0 ? (
                <Alert severity="info">No VOD streams in this category.</Alert>
              ) : (
                <Box sx={{ height: 400, width: '100%' }}>
                  <DataGrid
                    rows={vodStreams}
                    columns={streamColumns}
                    pageSizeOptions={[10, 20, 50]}
                    disableSelectionOnClick
                    onRowClick={(params) => {
                      sessionStorage.setItem('streamDetailReferrer', window.location.pathname);
                      navigate(`/streams/${params.row.id}/vod`);
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
            </>
          )}

          {/* Series Tab */}
          {tabValue === 2 && (
            <>
              {seriesStreams.length === 0 ? (
                <Alert severity="info">No series in this category.</Alert>
              ) : (
                <Box sx={{ height: 400, width: '100%' }}>
                  <DataGrid
                    rows={seriesStreams}
                    columns={streamColumns}
                    pageSizeOptions={[10, 20, 50]}
                    disableSelectionOnClick
                    onRowClick={(params) => {
                      sessionStorage.setItem('streamDetailReferrer', window.location.pathname);
                      navigate(`/streams/${params.row.id}/series`);
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
            </>
          )}
        </Box>
      </Card>
    </Box>
  );
}
