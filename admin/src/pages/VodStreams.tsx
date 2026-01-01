import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Box,
  Card,
  CircularProgress,
  Grid,
  Pagination,
  Typography,
  Chip,
  Alert,
  TextField,
  Snackbar,
  FormControl,
  Select,
  MenuItem,
  InputLabel,
} from '@mui/material';
import { DataGrid, type GridColDef } from '@mui/x-data-grid';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useAuthStore } from '../stores/authStore';
import { useSourceStore } from '../stores/sourceStore';
import streamsApi, { type Stream } from '../services/streamsApi';
import categoriesApi, { type Category } from '../services/categoriesApi';
import SourceSelector from '../components/SourceSelector';
import ViewToggle, { type ViewMode } from '../components/ViewToggle';
import StreamCard from '../components/StreamCard';

export default function VodStreams() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { isAuthenticated } = useAuthStore();
  const sourceId = useSourceStore((state) => state.selectedSourceId);
  const setSourceId = useSourceStore((state) => state.setSelectedSourceId);
  const [page, setPage] = useState(1);
  const [limit] = useState(20);
  const [view, setView] = useState<ViewMode>('list');
  const [searchQuery, setSearchQuery] = useState('');
  const [streamId, setStreamId] = useState('');
  const [allowDenyFilter, setAllowDenyFilter] = useState<'allow' | 'deny' | 'default' | 'all'>('all');
  const [snackbarOpen, setSnackbarOpen] = useState(false);
  const [snackbarMessage, setSnackbarMessage] = useState('');

  // Initialize selected category from sessionStorage if available
  const [selectedCategoryId, setSelectedCategoryId] = useState<number | null>(() => {
    const stored = sessionStorage.getItem('filterByCategoryId');
    if (stored) {
      sessionStorage.removeItem('filterByCategoryId');
      return parseInt(stored, 10);
    }
    return null;
  });

  // Mutation for updating allow_deny
  const updateAllowDenyMutation = useMutation({
    mutationFn: ({ id, allowDeny }: { id: number; allowDeny: string | null }) =>
      streamsApi.updateAllowDeny(id, allowDeny, 'vod'),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['streams-vod'] });
      setSnackbarMessage('Access control updated successfully');
      setSnackbarOpen(true);
    },
    onError: () => {
      setSnackbarMessage('Failed to update access control');
      setSnackbarOpen(true);
    },
  });

  // Fetch VOD streams with optional category filter, search, and stream_id
  const { data: streamsData, isLoading: isLoadingStreams, error: streamsError } = useQuery({
    queryKey: ['streams-vod', sourceId, selectedCategoryId, page, limit, searchQuery, streamId],
    queryFn: () => {
      if (!sourceId) return Promise.resolve(null);
      console.log('Fetching VOD with:', { sourceId, selectedCategoryId, page, limit, searchQuery, streamId });
      return streamsApi.getVodStreams(sourceId, selectedCategoryId || undefined, page, limit, searchQuery || undefined, streamId || undefined);
    },
    enabled: isAuthenticated && sourceId !== null,
  });

  // Fetch VOD categories for filtering
  const { data: categoriesData, isLoading: isLoadingCategories } = useQuery({
    queryKey: ['categories-vod', sourceId],
    queryFn: () => (sourceId ? categoriesApi.getCategories(sourceId, 1, 1000, undefined, 'vod') : Promise.resolve(null)),
    enabled: isAuthenticated && sourceId !== null,
  });

  // Categories are already filtered by type on backend
  const vodCategories = categoriesData?.data || [];

  let streams = streamsData?.data || [];
  const pagination = streamsData?.pagination;

  // Apply access control filter
  if (allowDenyFilter !== 'all') {
    streams = streams.filter((stream) => {
      if (allowDenyFilter === 'default') {
        return stream.allow_deny === null;
      }
      return stream.allow_deny === allowDenyFilter;
    });
  }

  // Debug logging
  if (selectedCategoryId !== null) {
    console.log('VOD streams filtered by category', selectedCategoryId, ':', streams.length, streams);
  }

  const categories: Record<string, string> = {};

  if (categoriesData?.data) {
    categoriesData.data.forEach((cat: Category) => {
      categories[cat.category_id] = cat.category_name;
    });
  }

  const getCategoryName = (categoryId: string | number | null): string => {
    if (!categoryId) return 'Unknown';
    return categories[categoryId] || `Category ${categoryId}`;
  };

  const columns: GridColDef[] = [
    { field: 'id', headerName: 'ID', width: 70 },
    { field: 'num', headerName: 'Order', width: 80 },
    { field: 'stream_id', headerName: 'Stream ID', width: 100 },
    { field: 'name', headerName: 'Name', width: 200, flex: 1 },
    {
      field: 'category_id',
      headerName: 'Category',
      width: 150,
      renderCell: (params) => getCategoryName(params.value),
    },
    {
      field: 'added',
      headerName: 'Added Date',
      width: 140,
      renderCell: (params) => {
        const addedDate = params.row.added_date;
        if (!addedDate) return '—';
        const date = new Date(addedDate);
        return date.toLocaleDateString('en-US', { year: 'numeric', month: 'short', day: 'numeric' });
      },
    },
    {
      field: 'rating',
      headerName: 'Rating',
      width: 90,
      renderCell: (params) => {
        const stream = streams.find((s) => s.id === params.row.id);
        const rating = stream?.data?.rating;
        if (!rating) return '—';
        return typeof rating === 'number' ? rating.toFixed(1) : rating;
      },
    },
    {
      field: 'releasedate',
      headerName: 'Release Date',
      width: 120,
      renderCell: (params) => {
        const releaseDate = params.row.release_date;
        if (!releaseDate) return '—';
        return releaseDate;
      },
    },
    {
      field: 'icon',
      headerName: 'Icon',
      width: 100,
      renderCell: (params) => {
        const stream = streams.find((s) => s.id === params.row.id);
        const iconUrl = stream?.data?.stream_icon;
        return iconUrl ? (
          <Box
            component="img"
            src={iconUrl}
            sx={{ width: 30, height: 30, borderRadius: 1, objectFit: 'cover' }}
            onError={(e) => {
              (e.target as HTMLImageElement).style.display = 'none';
            }}
          />
        ) : (
          <Typography variant="caption" sx={{ color: 'text.secondary' }}>
            N/A
          </Typography>
        );
      },
    },
    {
      field: 'is_adult',
      headerName: 'Adult',
      width: 80,
      renderCell: (params) => (params.value ? <Chip label="Adult" color="error" size="small" /> : '—'),
    },
    {
      field: 'created_at',
      headerName: 'Created',
      width: 140,
      renderCell: (params) => {
        const date = new Date(params.value);
        return date.toLocaleDateString('en-US', { year: 'numeric', month: 'short', day: 'numeric' });
      },
    },
    {
      field: 'allow_deny',
      headerName: 'Access Control',
      width: 120,
      sortable: false,
      filterable: false,
      renderCell: (params) => {
        const stream = params.row as Stream;
        const isLoading = updateAllowDenyMutation.isPending;
        const value = stream.allow_deny ?? 'default';

        const getColor = (val: string) => {
          switch (val) {
            case 'allow':
              return 'success';
            case 'deny':
              return 'error';
            default:
              return 'default';
          }
        };

        const getBgColor = (val: string) => {
          switch (val) {
            case 'allow':
              return '#c8e6c9';
            case 'deny':
              return '#ffcdd2';
            default:
              return '#f5f5f5';
          }
        };

        return (
          <FormControl size="small" sx={{ width: '100%' }} disabled={isLoading}>
            <Select
              value={value}
              onChange={(e) => {
                e.stopPropagation();
                const newValue = e.target.value;
                updateAllowDenyMutation.mutate({
                  id: stream.id,
                  allowDeny: newValue === 'default' ? null : (newValue as 'allow' | 'deny')
                });
              }}
              sx={{
                height: 32,
                fontSize: '0.875rem',
                backgroundColor: getBgColor(value),
                '& .MuiOutlinedInput-notchedOutline': {
                  borderColor: getColor(value) === 'success' ? '#4caf50' : getColor(value) === 'error' ? '#f44336' : '#bdbdbd',
                },
                '&:hover .MuiOutlinedInput-notchedOutline': {
                  borderColor: getColor(value) === 'success' ? '#4caf50' : getColor(value) === 'error' ? '#f44336' : '#bdbdbd',
                },
                '&.Mui-focused .MuiOutlinedInput-notchedOutline': {
                  borderColor: getColor(value) === 'success' ? '#4caf50' : getColor(value) === 'error' ? '#f44336' : '#bdbdbd',
                },
              }}
            >
              <MenuItem value="allow">Allow</MenuItem>
              <MenuItem value="deny">Deny</MenuItem>
              <MenuItem value="default">Default</MenuItem>
            </Select>
          </FormControl>
        );
      },
    },
  ];

  const handleCategorySelect = (categoryId: number | null) => {
    console.log('VOD Category selected:', categoryId, 'VOD categories:', vodCategories);
    setSelectedCategoryId(categoryId);
    setPage(1);
  };

  return (
    <Box sx={{ p: 3 }}>
      <Typography variant="h4" sx={{ mb: 3 }}>
        VOD Streams
      </Typography>

      {/* Source Selector and Search Bar */}
      <Card sx={{ mb: 3, p: 2 }}>
        <Box sx={{ display: 'flex', gap: 2, alignItems: 'flex-end', flexWrap: 'wrap' }}>
          <Box sx={{ flex: '0 0 auto', minWidth: 300 }}>
            <Typography variant="subtitle2" sx={{ mb: 2, fontWeight: 600 }}>
              Filter by Source
            </Typography>
            <SourceSelector
              sourceId={sourceId}
              onChange={(id) => {
                setSourceId(id);
                setPage(1);
                setSelectedCategoryId(null);
              }}
              required
            />
          </Box>

          {sourceId && (
            <>
              <TextField
                placeholder="Search by stream name..."
                value={searchQuery}
                onChange={(e) => {
                  setSearchQuery(e.target.value);
                  setPage(1);
                }}
                size="small"
                variant="outlined"
                sx={{ flex: '1 1 auto', minWidth: 200 }}
              />
              <TextField
                placeholder="Filter by stream ID..."
                value={streamId}
                onChange={(e) => {
                  setStreamId(e.target.value);
                  setPage(1);
                }}
                size="small"
                variant="outlined"
                sx={{ flex: '1 1 auto', minWidth: 200 }}
              />
              <FormControl sx={{ flex: '0 0 auto', minWidth: 160 }}>
                <InputLabel>Filter by Access Control</InputLabel>
                <Select
                  value={allowDenyFilter}
                  label="Filter by Access Control"
                  onChange={(e) => {
                    setAllowDenyFilter(e.target.value as 'allow' | 'deny' | 'default' | 'all');
                    setPage(1);
                  }}
                  size="small"
                >
                  <MenuItem value="all">All</MenuItem>
                  <MenuItem value="allow">Allow</MenuItem>
                  <MenuItem value="deny">Deny</MenuItem>
                  <MenuItem value="default">Default</MenuItem>
                </Select>
              </FormControl>
            </>
          )}
        </Box>
      </Card>

      {/* Main Content with Optional Sidebar */}
      {!isLoadingCategories && vodCategories.length > 0 && sourceId ? (
        <Box sx={{ display: 'flex', gap: 3 }}>
          {/* Left: Streams */}
          <Box sx={{ flex: '0 0 75%' }}>
          {/* View Controls */}
          {sourceId && (
            <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
              <Typography variant="body2" sx={{ color: 'text.secondary' }}>
                {pagination?.total || 0} streams
              </Typography>
              <ViewToggle view={view} onChange={setView} />
            </Box>
          )}

          {/* Error State */}
          {streamsError && (
            <Alert severity="error" sx={{ mb: 2 }}>
              Failed to load streams. Please try again.
            </Alert>
          )}

          {/* Loading State */}
          {isLoadingStreams && sourceId && (
            <Box sx={{ display: 'flex', justifyContent: 'center', p: 4 }}>
              <CircularProgress />
            </Box>
          )}

          {/* Empty State */}
          {!isLoadingStreams && sourceId && streams.length === 0 && (
            <Alert severity="info">
              {searchQuery
                ? 'No VOD streams match your search.'
                : selectedCategoryId
                  ? 'No VOD streams found in this category.'
                  : 'No VOD streams found for the selected source.'}
            </Alert>
          )}

          {/* List View */}
          {!isLoadingStreams && sourceId && view === 'list' && streams.length > 0 && (
            <>
              <Box sx={{ height: 500, width: '100%' }}>
                <DataGrid
                  rows={streams}
                  columns={columns}
                  pageSizeOptions={[10, 20, 50]}
                  disableSelectionOnClick
                  onRowClick={(params) => navigate(`/streams/${params.row.id}/vod`)}
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
          {!isLoadingStreams && sourceId && view === 'grid' && streams.length > 0 && (
            <>
              <Grid container spacing={2}>
                {streams.map((stream) => (
                  <Grid item xs={12} sm={6} md={4} lg={3} key={stream.id}>
                    <StreamCard
                      stream={stream}
                      categoryName={getCategoryName(stream.category_id)}
                      onClick={() => navigate(`/streams/${stream.id}/vod`)}
                    />
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

        {/* Right: Categories Sidebar */}
        <Box sx={{ flex: '0 0 calc(25% - 24px)' }}>
          <Card sx={{ position: 'sticky', top: 20, width: '100%', minHeight: 200, backgroundColor: '#fafafa' }}>
            <Box sx={{ p: 2, backgroundColor: 'background.paper' }}>
              <Typography variant="subtitle2" sx={{ fontWeight: 600, mb: 2 }}>
                Filter by Category ({vodCategories.length})
              </Typography>
              <Chip
                label="All Categories"
                onClick={() => handleCategorySelect(null)}
                variant={selectedCategoryId === null ? 'filled' : 'outlined'}
                color={selectedCategoryId === null ? 'primary' : 'default'}
                size="small"
                sx={{ width: '100%', mb: 1 }}
              />
            </Box>

            <Box sx={{ maxHeight: 400, overflow: 'auto', borderTop: '1px solid #e0e0e0' }}>
              {vodCategories.map((category: Category) => (
                <Box
                  key={category.id}
                  onClick={() => handleCategorySelect(Number(category.category_id))}
                  sx={{
                    p: 1.5,
                    px: 2,
                    cursor: 'pointer',
                    backgroundColor:
                      selectedCategoryId === Number(category.category_id) ? 'primary.light' : 'transparent',
                    '&:hover': {
                      backgroundColor:
                        selectedCategoryId === Number(category.category_id)
                          ? 'primary.light'
                          : 'action.hover',
                    },
                    borderBottom: '1px solid #f0f0f0',
                  }}
                >
                  <Typography variant="body2" sx={{ fontWeight: 500 }}>
                    {category.category_name}
                  </Typography>
                </Box>
              ))}
            </Box>
          </Card>
        </Box>
      </Box>
      ) : (
        <>
          {/* No categories: Full width streams */}
          {/* View Controls */}
          {sourceId && (
            <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
              <Typography variant="body2" sx={{ color: 'text.secondary' }}>
                {pagination?.total || 0} streams
              </Typography>
              <ViewToggle view={view} onChange={setView} />
            </Box>
          )}

          {/* Error State */}
          {streamsError && (
            <Alert severity="error" sx={{ mb: 2 }}>
              Failed to load streams. Please try again.
            </Alert>
          )}

          {/* Loading State */}
          {isLoadingStreams && sourceId && (
            <Box sx={{ display: 'flex', justifyContent: 'center', p: 4 }}>
              <CircularProgress />
            </Box>
          )}

          {/* Empty State */}
          {!isLoadingStreams && sourceId && streams.length === 0 && (
            <Alert severity="info">
              {searchQuery ? 'No VOD streams match your search.' : 'No VOD streams found for the selected source.'}
            </Alert>
          )}

          {/* List View */}
          {!isLoadingStreams && sourceId && view === 'list' && streams.length > 0 && (
            <>
              <Box sx={{ height: 500, width: '100%' }}>
                <DataGrid
                  rows={streams}
                  columns={columns}
                  pageSizeOptions={[10, 20, 50]}
                  disableSelectionOnClick
                  onRowClick={(params) => navigate(`/streams/${params.row.id}/vod`)}
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
          {!isLoadingStreams && sourceId && view === 'grid' && streams.length > 0 && (
            <>
              <Grid container spacing={2}>
                {streams.map((stream) => (
                  <Grid item xs={12} sm={6} md={4} lg={3} key={stream.id}>
                    <StreamCard
                      stream={stream}
                      categoryName={getCategoryName(stream.category_id)}
                      onClick={() => navigate(`/streams/${stream.id}/vod`)}
                    />
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
        </>
      )}

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
