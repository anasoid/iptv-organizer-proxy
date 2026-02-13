import { useParams, useNavigate } from 'react-router-dom';
import { useState } from 'react';
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
  ButtonGroup,
  Snackbar,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
} from '@mui/material';
import { DataGrid, type GridColDef } from '@mui/x-data-grid';
import { ArrowBack as ArrowBackIcon, CheckCircle as CheckCircleIcon, Block as BlockIcon, VisibilityOff as VisibilityOffIcon, Visibility as VisibilityIcon } from '@mui/icons-material';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useAuthStore } from '../stores/authStore';
import categoriesApi, { type Category } from '../services/categoriesApi';
import streamsApi from '../services/streamsApi';

export default function CategoryDetail() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { isAuthenticated } = useAuthStore();
  const queryClient = useQueryClient();
  const [snackbarOpen, setSnackbarOpen] = useState(false);
  const [snackbarMessage, setSnackbarMessage] = useState('');

  const categoryId = id ? parseInt(id, 10) : null;

  // Fetch category details
  const { data: categoryData, isLoading: isLoadingCategory, error: categoryError, refetch: refetchCategory } = useQuery({
    queryKey: ['category', categoryId],
    queryFn: () => (categoryId ? categoriesApi.getCategory(categoryId) : Promise.resolve(null)),
    enabled: isAuthenticated && categoryId !== null,
  });

  const category = categoryData?.data as Category | undefined;
  // Convert to lowercase to match MenuItem values (backend returns uppercase)
  const blackListValue = category ? ((category.blackList || category.black_list || 'default').toLowerCase()) : 'default';

  // Mutation for updating allow_deny
  const updateAllowDenyMutation = useMutation({
    mutationFn: (allowDeny: string | null) =>
      categoriesApi.updateAllowDeny(categoryId!, allowDeny),
    onSuccess: () => {
      setSnackbarMessage('Allow/Deny status updated successfully');
      setSnackbarOpen(true);
      refetchCategory();
    },
    onError: () => {
      setSnackbarMessage('Failed to update Allow/Deny status');
      setSnackbarOpen(true);
    },
  });

  // Mutation for updating blacklist
  const updateBlackListMutation = useMutation({
    mutationFn: (blackList: string) =>
      categoriesApi.updateBlackList(categoryId!, blackList as 'default' | 'hide' | 'visible' | 'force_hide' | 'force_visible'),
    onSuccess: () => {
      setSnackbarMessage('Blacklist status updated successfully');
      setSnackbarOpen(true);
      queryClient.invalidateQueries({ queryKey: ['category', categoryId] });
      refetchCategory();
    },
    onError: () => {
      setSnackbarMessage('Failed to update blacklist status');
      setSnackbarOpen(true);
    },
  });

  // Fetch streams only for the same type as the category
  const { data: streamsData, isLoading: isLoadingStreams } = useQuery({
    queryKey: ['category-streams', category?.sourceId, category?.externalId, category?.type],
    queryFn: () => {
      if (!category) return Promise.resolve(null);
      const categoryId = typeof category.externalId === 'string' ? parseInt(category.externalId, 10) : category.externalId;
      switch (category.type) {
        case 'live':
          return streamsApi.getLiveStreams(category.sourceId, categoryId);
        case 'vod':
          return streamsApi.getVodStreams(category.sourceId, categoryId);
        case 'series':
          return streamsApi.getSeriesStreams(category.sourceId, categoryId);
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
    { field: 'externalId', headerName: 'Stream ID', width: 100 },
    { field: 'name', headerName: 'Name', width: 200, flex: 1 },
    {
      field: 'isAdult',
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
              label={category.type.toUpperCase()}
              color={getCategoryTypeColor(category.type)}
              size="medium"
            />
          </Grid>
        </Grid>

        <Typography variant="h4" sx={{ mt: 2, mb: 1 }}>
          {category.name}
        </Typography>

        <Divider sx={{ my: 2 }} />

        <Grid container spacing={3}>
          <Grid item xs={12} sm={6}>
            <Typography variant="subtitle2" sx={{ fontWeight: 600, color: 'text.secondary' }}>
              Category ID
            </Typography>
            <Typography variant="body1">{category.externalId}</Typography>
          </Grid>

          <Grid item xs={12} sm={6}>
            <Typography variant="subtitle2" sx={{ fontWeight: 600, color: 'text.secondary' }}>
              Type
            </Typography>
            <Typography variant="body1" sx={{ textTransform: 'capitalize' }}>
              {category.type}
            </Typography>
          </Grid>

          <Grid item xs={12} sm={6}>
            <Typography variant="subtitle2" sx={{ fontWeight: 600, color: 'text.secondary' }}>
              Order Number
            </Typography>
            <Chip label={`#${category.num}`} color="primary" variant="outlined" />
          </Grid>

          <Grid item xs={12} sm={6}>
            <Typography variant="subtitle2" sx={{ fontWeight: 600, color: 'text.secondary', mb: 1 }}>
              Access Control
            </Typography>
            <ButtonGroup variant="outlined" size="small">
              <Button
                startIcon={<CheckCircleIcon />}
                variant={category.allowDeny === 'allow' ? 'contained' : 'outlined'}
                color={category.allowDeny === 'allow' ? 'success' : 'inherit'}
                onClick={() => updateAllowDenyMutation.mutate('allow')}
                disabled={updateAllowDenyMutation.isPending}
              >
                Allow
              </Button>
              <Button
                startIcon={<BlockIcon />}
                variant={category.allowDeny === 'deny' ? 'contained' : 'outlined'}
                color={category.allowDeny === 'deny' ? 'error' : 'inherit'}
                onClick={() => updateAllowDenyMutation.mutate('deny')}
                disabled={updateAllowDenyMutation.isPending}
              >
                Deny
              </Button>
              <Button
                variant={category.allowDeny === null ? 'contained' : 'outlined'}
                onClick={() => updateAllowDenyMutation.mutate(null)}
                disabled={updateAllowDenyMutation.isPending}
              >
                Default
              </Button>
            </ButtonGroup>
          </Grid>

          <Grid item xs={12} sm={6}>
            <Typography variant="subtitle2" sx={{ fontWeight: 600, color: 'text.secondary', mb: 1 }}>
              Blacklist Status
            </Typography>
            <FormControl fullWidth size="small" disabled={updateBlackListMutation.isPending}>
              <InputLabel>Blacklist</InputLabel>
              <Select
                value={blackListValue}
                label="Blacklist"
                onChange={(e) => {
                  updateBlackListMutation.mutate(e.target.value as string);
                }}
              >
                <MenuItem value="default">Default</MenuItem>
                <MenuItem value="hide">Hide</MenuItem>
                <MenuItem value="visible">Visible</MenuItem>
                <MenuItem value="force_hide">Force Hide</MenuItem>
                <MenuItem value="force_visible">Force Visible</MenuItem>
              </Select>
            </FormControl>
            {blackListValue && blackListValue !== 'default' && (
              <Box sx={{ mt: 1, display: 'flex', gap: 0.5 }}>
                <Chip
                  icon={blackListValue === 'hide' || blackListValue === 'force_hide' ? <VisibilityOffIcon /> : <VisibilityIcon />}
                  label={blackListValue.replace(/_/g, ' ').split(' ').map(w => w.charAt(0).toUpperCase() + w.slice(1)).join(' ')}
                  color={blackListValue === 'hide' || blackListValue === 'force_hide' ? 'error' : 'success'}
                  size="small"
                />
                {(blackListValue === 'force_hide' || blackListValue === 'force_visible') && (
                  <Chip label="Forced" size="small" variant="outlined" color="warning" />
                )}
              </Box>
            )}
          </Grid>

          {category.parentId && (
            <Grid item xs={12} sm={6}>
              <Typography variant="subtitle2" sx={{ fontWeight: 600, color: 'text.secondary' }}>
                Parent Category ID
              </Typography>
              <Typography variant="body1">{category.parentId}</Typography>
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
              {new Date(category.createdAt).toLocaleDateString('en-US', {
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
            {category.type === 'live'
              ? 'Live Streams'
              : category.type === 'vod'
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
              No {category.type === 'live' ? 'live streams' : category.type === 'vod' ? 'VOD streams' : 'series'} in this category.
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
                  navigate(`/streams/${params.row.id}/${category.type}`);
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

      {/* Snackbar for feedback */}
      <Snackbar
        open={snackbarOpen}
        autoHideDuration={4000}
        onClose={() => setSnackbarOpen(false)}
        message={snackbarMessage}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'left' }}
      />
    </Box>
  );
}
