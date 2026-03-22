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
  Select,
  MenuItem,
  FormControl,
  InputLabel,
  Snackbar,
} from '@mui/material';
import { DataGrid, type GridColDef } from '@mui/x-data-grid';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useAuthStore } from '../stores/authStore';
import { useSourceStore } from '../stores/sourceStore';
import categoriesApi, {
  type Category,
  type CategoryAllowDenyFilter,
  type CategoryBlackListFilter,
  type CategoryTypeFilter,
} from '../services/categoriesApi';
import SourceSelector from '../components/SourceSelector';
import ViewToggle, { type ViewMode } from '../components/ViewToggle';

type CategoryBlackListValue = 'default' | 'hide' | 'visible' | 'force_hide' | 'force_visible';

function getCategoryBlackListValue(category: Category): CategoryBlackListValue {
  const rawValue = (
    category.blackList
    ?? (category as Category & { black_list?: string | null }).black_list
    ?? 'default'
  ).toLowerCase();

  switch (rawValue) {
    case 'hide':
    case 'visible':
    case 'force_hide':
    case 'force_visible':
      return rawValue;
    default:
      return 'default';
  }
}

export default function Categories() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { isAuthenticated } = useAuthStore();
  const sourceId = useSourceStore((state) => state.selectedSourceId);
  const setSourceId = useSourceStore((state) => state.setSelectedSourceId);
  const [page, setPage] = useState(1);
  const [limit, setLimit] = useState(20);
  const [view, setView] = useState<ViewMode>('list');
  const [searchQuery, setSearchQuery] = useState('');
  const [categoryType, setCategoryType] = useState<CategoryTypeFilter | ''>('');
  const [allowDenyFilter, setAllowDenyFilter] = useState<CategoryAllowDenyFilter>('all');
  const [blackListFilter, setBlackListFilter] = useState<CategoryBlackListFilter>('all');
  const [snackbarOpen, setSnackbarOpen] = useState(false);
  const [snackbarMessage, setSnackbarMessage] = useState('');

  // Fetch categories with optional search and type filter
  const { data, isLoading, error } = useQuery({
    queryKey: ['categories', sourceId, page, limit, searchQuery, categoryType, allowDenyFilter, blackListFilter],
    queryFn: () => (
      sourceId
        ? categoriesApi.getCategories(
            sourceId,
            page,
            limit,
            searchQuery || undefined,
            categoryType || undefined,
            {
              allowDenyFilter,
              blackListFilter,
            },
          )
        : Promise.resolve(null)
    ),
    enabled: isAuthenticated && sourceId !== null,
  });

  // Mutation for updating allow_deny
  const updateAllowDenyMutation = useMutation({
    mutationFn: ({ id, allowDeny }: { id: number; allowDeny: string | null }) =>
      categoriesApi.updateAllowDeny(id, allowDeny),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['categories'] });
      setSnackbarMessage('Access control updated successfully');
      setSnackbarOpen(true);
    },
    onError: () => {
      setSnackbarMessage('Failed to update access control');
      setSnackbarOpen(true);
    },
  });

  // Mutation for updating blacklist
  const updateBlackListMutation = useMutation({
    mutationFn: ({ id, blackList }: { id: number; blackList: string }) =>
      categoriesApi.updateBlackList(id, blackList as 'default' | 'hide' | 'visible' | 'force_hide' | 'force_visible'),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['categories'] });
      setSnackbarMessage('Blacklist status updated successfully');
      setSnackbarOpen(true);
    },
    onError: () => {
      setSnackbarMessage('Failed to update blacklist status');
      setSnackbarOpen(true);
    },
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
    { field: 'num', headerName: 'Order', width: 80 },
    { field: 'name', headerName: 'Name', width: 200, flex: 1 },
    {
      field: 'type',
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
    { field: 'externalId', headerName: 'Category ID', width: 120 },
    { field: 'labels', headerName: 'Labels', width: 200, flex: 1 },
    {
      field: 'allowDeny',
      headerName: 'Access Control',
      width: 120,
      sortable: false,
      filterable: false,
      renderCell: (params) => {
        const category = params.row as Category;
        const isLoading = updateAllowDenyMutation.isPending;
        const value = category.allowDeny ?? 'default';

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
                  id: category.id,
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
    {
      field: 'blackList',
      headerName: 'Blacklist',
      width: 140,
      sortable: false,
      filterable: false,
      renderCell: (params) => {
        const category = params.row as Category;
        const isLoading = updateBlackListMutation.isPending;
        const value = getCategoryBlackListValue(category);

        const getBlackListColor = (val: string): 'success' | 'error' | 'warning' | 'info' | 'default' => {
          switch (val) {
            case 'hide':
            case 'force_hide':
              return 'error';
            case 'visible':
            case 'force_visible':
              return 'success';
            default:
              return 'default';
          }
        };

        const getBgColor = (val: string) => {
          switch (val) {
            case 'hide':
              return '#ffebee';
            case 'force_hide':
              return '#ef5350';
            case 'visible':
              return '#e8f5e9';
            case 'force_visible':
              return '#66bb6a';
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
                updateBlackListMutation.mutate({
                  id: category.id,
                  blackList: e.target.value as string,
                });
              }}
              sx={{
                height: 32,
                fontSize: '0.875rem',
                backgroundColor: getBgColor(value),
                '& .MuiOutlinedInput-notchedOutline': {
                  borderColor: getBlackListColor(value) === 'error' ? '#f44336' : getBlackListColor(value) === 'success' ? '#4caf50' : '#bdbdbd',
                },
                '&:hover .MuiOutlinedInput-notchedOutline': {
                  borderColor: getBlackListColor(value) === 'error' ? '#f44336' : getBlackListColor(value) === 'success' ? '#4caf50' : '#bdbdbd',
                },
                '&.Mui-focused .MuiOutlinedInput-notchedOutline': {
                  borderColor: getBlackListColor(value) === 'error' ? '#f44336' : getBlackListColor(value) === 'success' ? '#4caf50' : '#bdbdbd',
                },
              }}
            >
              <MenuItem value="default">Default</MenuItem>
              <MenuItem value="hide">Hide</MenuItem>
              <MenuItem value="visible">Visible</MenuItem>
              <MenuItem value="force_hide">Force Hide</MenuItem>
              <MenuItem value="force_visible">Force Visible</MenuItem>
            </Select>
          </FormControl>
        );
      },
    },
  ];

  const CategoryGridCard = ({ category }: { category: Category }) => {
    const blackListValue = getCategoryBlackListValue(category);

    return (
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
        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', mb: 1 }}>
          <Typography variant="subtitle2" component="div" sx={{ fontWeight: 600, flex: 1, minHeight: '2.4em' }}>
            {category.name}
          </Typography>
          <Chip label={`#${category.num}`} size="small" color="primary" variant="outlined" sx={{ ml: 1 }} />
        </Box>

        <Typography variant="caption" sx={{ color: 'text.secondary', display: 'block', mb: 1 }}>
          ID: {category.externalId}
        </Typography>

        <Box sx={{ display: 'flex', gap: 0.5, flexWrap: 'wrap', mt: 2 }}>
          <Chip
            label={category.type.toUpperCase()}
            color={getCategoryTypeColor(category.type)}
            size="small"
          />
          {category.allowDeny && (
            <Chip
              label={category.allowDeny.charAt(0).toUpperCase() + category.allowDeny.slice(1)}
              color={category.allowDeny === 'allow' ? 'success' : 'error'}
              size="small"
            />
          )}
          {blackListValue !== 'default' && (
            <Chip
              label={blackListValue.replace(/_/g, ' ').split(' ').map(w => w.charAt(0).toUpperCase() + w.slice(1)).join(' ')}
              color={blackListValue === 'hide' || blackListValue === 'force_hide' ? 'error' : 'success'}
              size="small"
            />
          )}
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
  };

  return (
    <Box sx={{ p: 3 }}>
      <Typography variant="h4" sx={{ mb: 3 }}>
        Categories
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

          {sourceId && (
            <FormControl sx={{ flex: '0 0 auto', minWidth: 150 }}>
              <InputLabel>Filter by Type</InputLabel>
              <Select
                value={categoryType}
                label="Filter by Type"
                onChange={(e) => {
                  setCategoryType(e.target.value as CategoryTypeFilter | '');
                  setPage(1);
                }}
                size="small"
              >
                <MenuItem value="">All Types</MenuItem>
                <MenuItem value="live">Live</MenuItem>
                <MenuItem value="vod">VOD</MenuItem>
                <MenuItem value="series">Series</MenuItem>
              </Select>
            </FormControl>
          )}

          {sourceId && (
            <FormControl sx={{ flex: '0 0 auto', minWidth: 160 }}>
              <InputLabel>Filter by Access Control</InputLabel>
              <Select
                value={allowDenyFilter}
                label="Filter by Access Control"
                onChange={(e) => {
                  setAllowDenyFilter(e.target.value as CategoryAllowDenyFilter);
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
          )}

          {sourceId && (
            <FormControl sx={{ flex: '0 0 auto', minWidth: 160 }}>
              <InputLabel>Filter by Blacklist</InputLabel>
              <Select
                value={blackListFilter}
                label="Filter by Blacklist"
                onChange={(e) => {
                  setBlackListFilter(e.target.value as CategoryBlackListFilter);
                  setPage(1);
                }}
                size="small"
              >
                <MenuItem value="all">All</MenuItem>
                <MenuItem value="default">Default</MenuItem>
                <MenuItem value="hidden">Hidden</MenuItem>
                <MenuItem value="visible">Visible</MenuItem>
                <MenuItem value="force_hidden">Force (Hidden/Visible)</MenuItem>
              </Select>
            </FormControl>
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
      {!isLoading && sourceId && view === 'list' && (
        <>
          {categories.length > 0 ? (
            <>
              <Box sx={{ height: 500, width: '100%' }}>
                <DataGrid
                  rows={categories}
                  columns={columns}
                  disableSelectionOnClick
                  onRowClick={(params) => navigate(`/categories/${params.row.id}`)}
                  hideFooter
                  sx={{
                    '& .MuiDataGrid-cell': { py: 1 },
                    '& .MuiDataGrid-row': { cursor: 'pointer' },
                  }}
                />
              </Box>
            </>
          ) : null}
          {pagination && pagination.pages > 1 && (
            <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mt: 2 }}>
              <Box sx={{ display: 'flex', justifyContent: 'center', flex: 1 }}>
                <Pagination
                  count={pagination.pages}
                  page={page}
                  onChange={(_, value) => setPage(value)}
                  color="primary"
                />
              </Box>
              <Box sx={{ ml: 2, display: 'flex', gap: 1, alignItems: 'center' }}>
                <Typography variant="body2" sx={{ mr: 1 }}>Page size:</Typography>
                <FormControl sx={{ minWidth: 80 }} size="small">
                  <Select
                    value={limit}
                    onChange={(e) => {
                      setLimit(Number(e.target.value));
                      setPage(1);
                    }}
                  >
                    <MenuItem value={10}>10</MenuItem>
                    <MenuItem value={20}>20</MenuItem>
                    <MenuItem value={50}>50</MenuItem>
                  </Select>
                </FormControl>
              </Box>
            </Box>
          )}
        </>
      )}

      {/* Grid View */}
      {!isLoading && sourceId && view === 'grid' && (
        <>
          {categories.length > 0 ? (
            <>
              <Grid container spacing={2}>
                {categories.map((category) => (
                  <Grid item xs={12} sm={6} md={4} lg={3} key={category.id}>
                    <CategoryGridCard category={category} />
                  </Grid>
                ))}
              </Grid>
            </>
          ) : (
            <Alert severity="info">
              {searchQuery ? 'No categories match your search.' : 'No categories found for the selected source.'}
            </Alert>
          )}
          {pagination && pagination.pages > 1 && (
            <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mt: 3 }}>
              <Box sx={{ display: 'flex', justifyContent: 'center', flex: 1 }}>
                <Pagination
                  count={pagination.pages}
                  page={page}
                  onChange={(_, value) => setPage(value)}
                  color="primary"
                />
              </Box>
              <Box sx={{ ml: 2, display: 'flex', gap: 1, alignItems: 'center' }}>
                <Typography variant="body2" sx={{ mr: 1 }}>Page size:</Typography>
                <FormControl sx={{ minWidth: 80 }} size="small">
                  <Select
                    value={limit}
                    onChange={(e) => {
                      setLimit(Number(e.target.value));
                      setPage(1);
                    }}
                  >
                    <MenuItem value={10}>10</MenuItem>
                    <MenuItem value={20}>20</MenuItem>
                    <MenuItem value={50}>50</MenuItem>
                  </Select>
                </FormControl>
              </Box>
            </Box>
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
