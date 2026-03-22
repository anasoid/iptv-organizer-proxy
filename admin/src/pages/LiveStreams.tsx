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
import CategoryFilterSidebar from '../components/CategoryFilterSidebar';
import { getCategoryDisplayName } from '../utils/categoryDisplayName';
import { useDebounce } from '../hooks/useDebounce';

export default function LiveStreams() {
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
      streamsApi.updateAllowDeny(id, allowDeny, 'live'),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['streams-live'] });
      setSnackbarMessage('Access control updated successfully');
      setSnackbarOpen(true);
    },
    onError: () => {
      setSnackbarMessage('Failed to update access control');
      setSnackbarOpen(true);
    },
  });

  // Debounce the search query so API is only called after user stops typing
  const debouncedSearchQuery = useDebounce(searchQuery); // use default delay

  // Fetch live streams with optional category filter, search, and stream_id
  const { data: streamsData, isLoading: isLoadingStreams, error: streamsError } = useQuery({
    queryKey: ['streams-live', sourceId, selectedCategoryId, page, limit, debouncedSearchQuery, streamId],
    queryFn: () => {
      if (!sourceId) return Promise.resolve(null);
      // Only use debouncedSearchQuery here
      return streamsApi.getLiveStreams(sourceId, selectedCategoryId || undefined, page, limit, debouncedSearchQuery || undefined, streamId || undefined);
    },
    enabled: isAuthenticated && sourceId !== null,
  });

  // Fetch live categories for filtering
  const { data: categoriesData, isLoading: isLoadingCategories } = useQuery({
    queryKey: ['categories-live', sourceId],
    queryFn: () => (sourceId ? categoriesApi.getCategories(sourceId, 1, 1000, undefined, 'live') : Promise.resolve(null)),
    enabled: isAuthenticated && sourceId !== null,
  });

  // Categories are already filtered by type on backend
  const liveCategories = categoriesData?.data || [];

  let streams = streamsData?.data?.map((s) => ({ ...s, icon: true })) || [];
  const pagination = streamsData?.pagination;

  // Apply access control filter
  if (allowDenyFilter !== 'all') {
    streams = streams.filter((stream) => {
      if (allowDenyFilter === 'default') {
        return stream.allowDeny === null;
      }
      return stream.allowDeny === allowDenyFilter;
    });
  }

  // Debug logging
  if (selectedCategoryId !== null) {
    console.log('Live streams filtered by category', selectedCategoryId, ':', streams.length, streams);
  }

  const categories: Record<string, string> = {};

  if (categoriesData?.data) {
    categoriesData.data.forEach((cat: Category) => {
      categories[cat.externalId] = getCategoryDisplayName(cat);
    });
  }

  const getCategoryName = (categoryId: string | number | null): string => {
    if (!categoryId) return 'Unknown';
    return categories[categoryId] || `Category ${categoryId}`;
  };

  const columns: GridColDef[] = [
    { field: 'id', headerName: 'ID', width: 50 },
    { field: 'num', headerName: 'Order', width: 60 },
    { field: 'externalId', headerName: 'Stream ID', width: 100 },
    { field: 'name', headerName: 'Name', width: 350, flex: 1 },
    {
      field: 'categoryId',
      headerName: 'Category',
      width: 150,
      renderCell: (params) => getCategoryName(params.value),
    },
    {
      field: 'icon',
      headerName: 'Icon',
      width: 100,
      renderCell: (params) => {
        let dataObj: Record<string, any> | null = null;
        const data = params.row.data;
        if (data) {
          if (typeof data === 'string') {
            try {
              dataObj = JSON.parse(data);
            } catch {
              dataObj = null;
            }
          } else {
            dataObj = data as Record<string, any>;
          }
        }
        const iconUrl = dataObj?.stream_icon || dataObj?.cover;
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
      field: 'isAdult',
      headerName: 'Adult',
      width: 80,
      renderCell: (params) => (params.value ? <Chip label="Adult" color="error" size="small" /> : '—'),
    },
    {
      field: 'createdAt',
      headerName: 'Created',
      width: 140,
      renderCell: (params) => {
        const date = new Date(params.value);
        return date.toLocaleDateString('en-US', { year: 'numeric', month: 'short', day: 'numeric' });
      },
    },
    {
      field: 'allowDeny',
      headerName: 'Access Control',
      width: 120,
      sortable: false,
      filterable: false,
      renderCell: (params) => {
        const stream = params.row as Stream;
        const isLoading = updateAllowDenyMutation.isPending;
        const value = stream.allowDeny ?? 'default';

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
    console.log('Live Category selected:', categoryId, 'Live categories:', liveCategories);
    setSelectedCategoryId(categoryId);
    setPage(1);
  };

  return (
    <Box sx={{ p: 3 }}>
      <Typography variant="h4" sx={{ mb: 3 }}>
        Live Streams
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
      {!isLoadingCategories && liveCategories.length > 0 && sourceId ? (
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
                ? 'No live streams match your search.'
                : selectedCategoryId
                  ? 'No live streams found in this category.'
                  : 'No live streams found for the selected source.'}
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
                  onRowClick={(params) => navigate(`/streams/${params.row.id}/live`)}
                  sx={{
                    '& .MuiDataGrid-cell': { py: 1 },
                    '& .MuiDataGrid-row': { cursor: 'pointer' },
                  }}
                  initialState={{
                    pagination: { paginationModel: { pageSize: limit } },
                    columns: { columnVisibilityModel: { num: false, is_adult: false, icon: true } },
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
              <Box
                sx={{
                  display: 'grid',
                  gridTemplateColumns: 'repeat(5, 1fr)',
                  gap: 2,
                }}
              >
                {streams.map((stream) => (
                  <Box key={stream.id} sx={{ minWidth: 0 }}>
                    <StreamCard
                      stream={stream}
                      categoryName={getCategoryName(stream.categoryId)}
                      onClick={() => navigate(`/streams/${stream.id}/live`)}
                    />
                  </Box>
                ))}
              </Box>
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

        <CategoryFilterSidebar
          categories={liveCategories}
          selectedCategoryId={selectedCategoryId}
          onCategorySelect={handleCategorySelect}
        />
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
              {searchQuery ? 'No live streams match your search.' : 'No live streams found for the selected source.'}
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
                  onRowClick={(params) => navigate(`/streams/${params.row.id}/live`)}
                  sx={{
                    '& .MuiDataGrid-cell': { py: 1 },
                    '& .MuiDataGrid-row': { cursor: 'pointer' },
                  }}
                  initialState={{
                    pagination: { paginationModel: { pageSize: limit } },
                    columns: { columnVisibilityModel: { num: false, is_adult: false } },
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
                      categoryName={getCategoryName(stream.categoryId)}
                      onClick={() => navigate(`/streams/${stream.id}/live`)}
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
