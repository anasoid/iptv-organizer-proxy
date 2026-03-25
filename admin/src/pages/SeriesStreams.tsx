import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Box,
  Button,
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
import {
  DataGrid,
  type GridColDef,
  type GridColumnVisibilityModel,
  type GridSortModel,
} from '@mui/x-data-grid';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useAuthStore } from '../stores/authStore';
import { useSourceStore } from '../stores/sourceStore';
import streamsApi, { type Stream } from '../services/streamsApi';
import categoriesApi, { type Category } from '../services/categoriesApi';
import SourceSelector from '../components/SourceSelector';
import ViewToggle, { type ViewMode } from '../components/ViewToggle';
import StreamCard from '../components/StreamCard';
import StreamDateFilterField from '../components/StreamDateFilterField';
import CategoryFilterSidebar from '../components/CategoryFilterSidebar';
import { getCategoryDisplayName } from '../utils/categoryDisplayName';
import { formatDisplayDate } from '../utils/dateFormat';
import { useDebounce } from '../hooks/useDebounce';

export default function SeriesStreams() {
  const columnVisibilityStorageKey = 'streams-series-column-visibility';
  const defaultColumnVisibilityModel: GridColumnVisibilityModel = {
    num: false,
    isAdult: false,
    addedDate: false,
    releaseDate: false,
    rating: false,
    tmdb: false,
  };

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
  const [showAdvancedFilters, setShowAdvancedFilters] = useState(false);
  const [sortModel, setSortModel] = useState<GridSortModel>([]);
  const [columnVisibilityModel, setColumnVisibilityModel] = useState<GridColumnVisibilityModel>(
    () => {
      const stored = localStorage.getItem(columnVisibilityStorageKey);
      if (!stored) {
        return defaultColumnVisibilityModel;
      }
      try {
        return { ...defaultColumnVisibilityModel, ...JSON.parse(stored) };
      } catch {
        return defaultColumnVisibilityModel;
      }
    }
  );
  const [addedDateFrom, setAddedDateFrom] = useState('');
  const [addedDateTo, setAddedDateTo] = useState('');
  const [createdDateFrom, setCreatedDateFrom] = useState('');
  const [createdDateTo, setCreatedDateTo] = useState('');
  const [updateDateFrom, setUpdateDateFrom] = useState('');
  const [updateDateTo, setUpdateDateTo] = useState('');
  const [releaseDateFrom, setReleaseDateFrom] = useState('');
  const [releaseDateTo, setReleaseDateTo] = useState('');
  const [ratingMin, setRatingMin] = useState('');
  const [ratingMax, setRatingMax] = useState('');
  const [tmdb, setTmdb] = useState('');
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
      streamsApi.updateAllowDeny(id, allowDeny, 'series'),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['streams-series'] });
      setSnackbarMessage('Access control updated successfully');
      setSnackbarOpen(true);
    },
    onError: () => {
      setSnackbarMessage('Failed to update access control');
      setSnackbarOpen(true);
    },
  });

  // Debounce the search query so API is only called after user stops typing
  const debouncedSearchQuery = useDebounce(searchQuery);

  const sort = sortModel[0];
  const queryOptions = {
    sortBy: sort?.field as 'addedDate' | 'createdAt' | 'updatedAt' | 'releaseDate' | 'rating' | 'tmdb' | undefined,
    sortDir: sort?.sort as 'asc' | 'desc' | undefined,
    addedDateFrom: addedDateFrom || undefined,
    addedDateTo: addedDateTo || undefined,
    createdDateFrom: createdDateFrom || undefined,
    createdDateTo: createdDateTo || undefined,
    updateDateFrom: updateDateFrom || undefined,
    updateDateTo: updateDateTo || undefined,
    releaseDateFrom: releaseDateFrom || undefined,
    releaseDateTo: releaseDateTo || undefined,
    ratingMin: ratingMin !== '' ? Number(ratingMin) : undefined,
    ratingMax: ratingMax !== '' ? Number(ratingMax) : undefined,
    tmdb: tmdb !== '' ? Number.parseInt(tmdb, 10) : undefined,
  };

  // Fetch series streams with optional category filter, search, and stream_id
  const { data: streamsData, isLoading: isLoadingStreams, error: streamsError } = useQuery({
    queryKey: [
      'streams-series',
      sourceId,
      selectedCategoryId,
      page,
      limit,
      debouncedSearchQuery,
      streamId,
      queryOptions,
    ],
    queryFn: () => {
      if (!sourceId) return Promise.resolve(null);
      return streamsApi.getSeriesStreams(
        sourceId,
        selectedCategoryId || undefined,
        page,
        limit,
        debouncedSearchQuery || undefined,
        streamId || undefined,
        queryOptions
      );
    },
    enabled: isAuthenticated && sourceId !== null,
  });

  // Fetch series categories for filtering
  const { data: categoriesData, isLoading: isLoadingCategories } = useQuery({
    queryKey: ['categories-series', sourceId],
    queryFn: () => (sourceId ? categoriesApi.getCategories(sourceId, 1, 1000, undefined, 'series') : Promise.resolve(null)),
    enabled: isAuthenticated && sourceId !== null,
  });

  // Categories are already filtered by type on backend
  const seriesCategories = categoriesData?.data || [];

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

  if (queryOptions.tmdb !== undefined) {
    streams = streams.filter((stream) => stream.tmdb === queryOptions.tmdb);
  }

  // Debug logging
  if (selectedCategoryId !== null) {
    console.log('Streams filtered by category', selectedCategoryId, ':', streams.length, streams);
  }

  const categories: Record<string, string> = {};

  if (categoriesData?.data) {
    categoriesData.data.forEach((cat: Category) => {
      const displayName = getCategoryDisplayName(cat);
      categories[String(cat.externalId)] = displayName;
    });
  }

  const missingCategoryIds = Array.from(
    new Set(
      streams
        .map((stream) => stream.categoryId)
        .filter(
          (categoryId): categoryId is number =>
            categoryId !== null && categoryId !== undefined && !categories[String(categoryId)]
        )
    )
  );

  const { data: missingCategoryNames } = useQuery({
    queryKey: ['stream-category-names-series', sourceId, missingCategoryIds],
    queryFn: async () => {
      if (!sourceId || missingCategoryIds.length === 0) {
        return {} as Record<string, string>;
      }

      const entries = await Promise.all(
        missingCategoryIds.map(async (externalId) => {
          try {
            const result = await categoriesApi.getCategoryByExternalId(externalId, sourceId, 'series');
            return [String(externalId), getCategoryDisplayName(result.data)] as const;
          } catch {
            return [String(externalId), `Category ${externalId}`] as const;
          }
        })
      );

      return Object.fromEntries(entries);
    },
    enabled: isAuthenticated && sourceId !== null && missingCategoryIds.length > 0,
  });

  const categoryNames = {
    ...categories,
    ...(missingCategoryNames ?? {}),
  };

  const getCategoryName = (categoryId: string | number | null): string => {
    if (!categoryId) return 'Unknown';
    const categoryName = categoryNames[String(categoryId)];
    if (categoryName) {
      return categoryName;
    }
    return `Category ${categoryId}`;
  };

  const asRecord = (value: unknown): Record<string, unknown> | null => {
    if (!value || typeof value !== 'object') {
      return null;
    }
    return value as Record<string, unknown>;
  };

  const handleColumnVisibilityModelChange = (model: GridColumnVisibilityModel) => {
    setColumnVisibilityModel(model);
    localStorage.setItem(columnVisibilityStorageKey, JSON.stringify(model));
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
        const stream = streams.find((s) => s.id === params.row.id);
        let dataObj: Record<string, unknown> | null = null;
        if (stream?.data) {
          if (typeof stream.data === 'string') {
            try {
              dataObj = asRecord(JSON.parse(stream.data));
            } catch {
              dataObj = null;
            }
          } else {
            dataObj = asRecord(stream.data);
          }
        }
        const iconUrl =
          (typeof dataObj?.stream_icon === 'string' && dataObj.stream_icon) ||
          (typeof dataObj?.cover === 'string' && dataObj.cover) ||
          null;
        // Debug log
        console.log('Series Icon render', { id: stream?.id, iconUrl, dataObj });
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
      field: 'addedDate',
      headerName: 'Added Date',
      width: 130,
      renderCell: (params) => formatDisplayDate(params.value),
    },
    {
      field: 'createdAt',
      headerName: 'Created',
      width: 140,
      renderCell: (params) => formatDisplayDate(params.value),
    },
    {
      field: 'updatedAt',
      headerName: 'Updated',
      width: 140,
      renderCell: (params) => formatDisplayDate(params.value),
    },
    {
      field: 'releaseDate',
      headerName: 'Release Date',
      width: 140,
      renderCell: (params) => formatDisplayDate(params.value),
    },
    {
      field: 'rating',
      headerName: 'Rating',
      width: 90,
      renderCell: (params) => (params.value ?? '—'),
    },
    {
      field: 'tmdb',
      headerName: 'TMDB',
      width: 110,
      renderCell: (params) => (params.value ?? '—'),
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

        return (
          <FormControl size="small" sx={{ width: '100%' }} disabled={isLoading}>
            <Select
              value={stream.allowDeny ?? 'default'}
              onChange={(e) => {
                e.stopPropagation();
                const value = e.target.value;
                updateAllowDenyMutation.mutate({
                  id: stream.id,
                  allowDeny: value === 'default' ? null : (value as 'allow' | 'deny')
                });
              }}
              sx={{ height: 32, fontSize: '0.875rem' }}
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
    console.log('Category selected:', categoryId, 'Series categories:', seriesCategories);
    setSelectedCategoryId(categoryId);
    setPage(1);
  };

  return (
    <Box sx={{ p: 3 }}>
      <Typography variant="h4" sx={{ mb: 3 }}>
        Series
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
                placeholder="Search by series name..."
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
              <Button
                variant="outlined"
                size="small"
                onClick={() => setShowAdvancedFilters((current) => !current)}
              >
                {showAdvancedFilters ? 'Hide Advanced Filters' : 'Show Advanced Filters'}
              </Button>
            </>
          )}
        </Box>

        {sourceId && showAdvancedFilters && (
          <Box sx={{ mt: 2, display: 'grid', gridTemplateColumns: 'repeat(5, minmax(140px, 1fr))', gap: 1.5 }}>
            <StreamDateFilterField label="Added From" value={addedDateFrom} onChange={(value) => { setAddedDateFrom(value); setPage(1); }} />
            <StreamDateFilterField label="Added To" value={addedDateTo} onChange={(value) => { setAddedDateTo(value); setPage(1); }} />
            <StreamDateFilterField label="Created From" value={createdDateFrom} onChange={(value) => { setCreatedDateFrom(value); setPage(1); }} />
            <StreamDateFilterField label="Created To" value={createdDateTo} onChange={(value) => { setCreatedDateTo(value); setPage(1); }} />
            <StreamDateFilterField label="Updated From" value={updateDateFrom} onChange={(value) => { setUpdateDateFrom(value); setPage(1); }} />
            <StreamDateFilterField label="Updated To" value={updateDateTo} onChange={(value) => { setUpdateDateTo(value); setPage(1); }} />
            <StreamDateFilterField label="Release From" value={releaseDateFrom} onChange={(value) => { setReleaseDateFrom(value); setPage(1); }} />
            <StreamDateFilterField label="Release To" value={releaseDateTo} onChange={(value) => { setReleaseDateTo(value); setPage(1); }} />
            <TextField
              label="Rating Min"
              type="number"
              value={ratingMin}
              onChange={(e) => { setRatingMin(e.target.value); setPage(1); }}
              size="small"
            />
            <TextField
              label="Rating Max"
              type="number"
              value={ratingMax}
              onChange={(e) => { setRatingMax(e.target.value); setPage(1); }}
              size="small"
            />
            <TextField
              label="TMDB"
              type="number"
              value={tmdb}
              onChange={(e) => { setTmdb(e.target.value); setPage(1); }}
              size="small"
            />
          </Box>
        )}
      </Card>

      {/* Main Content with Optional Sidebar */}
      {!isLoadingCategories && seriesCategories.length > 0 && sourceId ? (
        <Box sx={{ display: 'flex', gap: 3 }}>
          {/* Left: Streams */}
          <Box sx={{ flex: '0 0 75%' }}>
          {/* View Controls */}
          {sourceId && (
            <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
              <Typography variant="body2" sx={{ color: 'text.secondary' }}>
                {pagination?.total || 0} series
              </Typography>
              <ViewToggle view={view} onChange={setView} />
            </Box>
          )}

          {/* Error State */}
          {streamsError && (
            <Alert severity="error" sx={{ mb: 2 }}>
              Failed to load series. Please try again.
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
                ? 'No series match your search.'
                : selectedCategoryId
                  ? 'No series found in this category.'
                  : 'No series found for the selected source.'}
            </Alert>
          )}

          {/* List View */}
          {!isLoadingStreams && sourceId && view === 'list' && streams.length > 0 && (
            <>
              <Box sx={{ height: 500, width: '100%' }}>
                <DataGrid
                  rows={streams}
                  columns={columns}
                  columnVisibilityModel={columnVisibilityModel}
                  onColumnVisibilityModelChange={handleColumnVisibilityModelChange}
                  sortingMode="server"
                  sortModel={sortModel}
                  onSortModelChange={(model) => {
                    setSortModel(model);
                    setPage(1);
                  }}
                  pageSizeOptions={[10, 20, 50]}
                  disableSelectionOnClick
                  onRowClick={(params) => navigate(`/streams/${params.row.id}/series`)}
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
                      onClick={() => navigate(`/streams/${stream.id}/series`)}
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
          categories={seriesCategories}
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
                {pagination?.total || 0} series
              </Typography>
              <ViewToggle view={view} onChange={setView} />
            </Box>
          )}

          {/* Error State */}
          {streamsError && (
            <Alert severity="error" sx={{ mb: 2 }}>
              Failed to load series. Please try again.
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
              {searchQuery ? 'No series match your search.' : 'No series found for the selected source.'}
            </Alert>
          )}

          {/* List View */}
          {!isLoadingStreams && sourceId && view === 'list' && streams.length > 0 && (
            <>
              <Box sx={{ height: 500, width: '100%' }}>
                <DataGrid
                  rows={streams}
                  columns={columns}
                  columnVisibilityModel={columnVisibilityModel}
                  onColumnVisibilityModelChange={handleColumnVisibilityModelChange}
                  sortingMode="server"
                  sortModel={sortModel}
                  onSortModelChange={(model) => {
                    setSortModel(model);
                    setPage(1);
                  }}
                  pageSizeOptions={[10, 20, 50]}
                  disableSelectionOnClick
                  onRowClick={(params) => navigate(`/streams/${params.row.id}/series`)}
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
                      categoryName={getCategoryName(stream.categoryId)}
                      onClick={() => navigate(`/streams/${stream.id}/series`)}
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
