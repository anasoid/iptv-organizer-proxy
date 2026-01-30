import { useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
  Box,
  Paper,
  Typography,
  Button,
  Grid,
  Card,
  CardContent,
  CardActions,
  CircularProgress,
  Alert,
  Chip,
  Divider,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  FormControl,
  Select,
  MenuItem,
  Stack,
  Pagination,
} from '@mui/material';
import { useQuery, useMutation } from '@tanstack/react-query';
import { ArrowBack, Download as DownloadIcon, Block as BlockIcon, OpenInNew as OpenInNewIcon, Visibility as ViewIcon } from '@mui/icons-material';
import clientsApi from '../services/clientsApi';
import sourcesApi from '../services/sourcesApi';
import { useAuthStore } from '../stores/authStore';

// Type definitions for category and stream data
interface Category {
  category_id: string | number;
  category_name: string;
  parent_id?: number | null;
}

interface Stream {
  id?: number;
  name: string;
  num?: string | number | null;
  category_id: string | number;
  category_name?: string;
}

type AllowedItem = Category | Stream;

interface ApiResponse<T> {
  success: boolean;
  data: T;
}

interface ApiErrorResponse {
  response?: {
    data?: {
      message?: string;
    };
  };
}

const EXPORT_TYPES = [
  { id: 'live_categories', label: 'Live Categories', variant: undefined },
  { id: 'live_streams', label: 'Live Streams', variant: undefined },
  { id: 'vod_categories', label: 'VOD Categories', variant: undefined },
  { id: 'vod_streams', label: 'VOD Streams', variant: undefined },
  { id: 'series_categories', label: 'Series Categories', variant: undefined },
  { id: 'series', label: 'Series', variant: undefined },
];

const BLOCKED_TYPES = [
  { id: 'blocked_live_categories', label: 'Blocked Live Categories', type: 'live_categories' },
  { id: 'blocked_live_streams', label: 'Blocked Live Streams', type: 'live_streams' },
  { id: 'blocked_vod_categories', label: 'Blocked VOD Categories', type: 'vod_categories' },
  { id: 'blocked_vod_streams', label: 'Blocked VOD Streams', type: 'vod_streams' },
  { id: 'blocked_series_categories', label: 'Blocked Series Categories', type: 'series_categories' },
  { id: 'blocked_series', label: 'Blocked Series', type: 'series' },
];

export default function ClientDetail() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { isAuthenticated } = useAuthStore();
  const [exportingType, setExportingType] = useState<string | null>(null);
  const [exportMessage, setExportMessage] = useState<{ type: 'success' | 'error'; message: string } | null>(null);
  const [blockedModalOpen, setBlockedModalOpen] = useState(false);
  const [selectedBlockedType, setSelectedBlockedType] = useState<string | null>(null);
  const [allowedModalOpen, setAllowedModalOpen] = useState(false);
  const [selectedAllowedType, setSelectedAllowedType] = useState<string | null>(null);
  const [blockedSearch, setBlockedSearch] = useState('');
  const [blockedPageSize, setBlockedPageSize] = useState(20);
  const [blockedPage, setBlockedPage] = useState(1);
  const [allowedSearch, setAllowedSearch] = useState('');
  const [allowedPageSize, setAllowedPageSize] = useState(20);
  const [allowedPage, setAllowedPage] = useState(1);

  const { data: clientResponse, isLoading: clientLoading, error: clientError } = useQuery({
    queryKey: ['client', id],
    queryFn: () => clientsApi.getClient(Number(id)),
    enabled: isAuthenticated && !!id,
  });

  const client = clientResponse?.data;

  const { data: sourceResponse } = useQuery({
    queryKey: ['source', client?.sourceId],
    queryFn: () => sourcesApi.getSource(Number(client?.sourceId)),
    enabled: isAuthenticated && !!client?.sourceId,
  });

  // Fetch all categories (both allowed and blocked) to map category_id to category_name
  const { data: liveCategoriesResponse } = useQuery({
    queryKey: ['all-live-categories', id],
    queryFn: () => clientsApi.exportLiveCategories(Number(id)),
    enabled: isAuthenticated && !!id,
  });

  const { data: vodCategoriesResponse } = useQuery({
    queryKey: ['all-vod-categories', id],
    queryFn: () => clientsApi.exportVodCategories(Number(id)),
    enabled: isAuthenticated && !!id,
  });

  const { data: seriesCategoriesResponse } = useQuery({
    queryKey: ['all-series-categories', id],
    queryFn: () => clientsApi.exportSeriesCategories(Number(id)),
    enabled: isAuthenticated && !!id,
  });

  const { data: blockedLiveCategoriesResponse } = useQuery({
    queryKey: ['blocked-live-categories', id],
    queryFn: () => clientsApi.exportBlockedLiveCategories(Number(id)),
    enabled: isAuthenticated && !!id && !!client?.filterId,
  });

  const { data: blockedVodCategoriesResponse } = useQuery({
    queryKey: ['blocked-vod-categories', id],
    queryFn: () => clientsApi.exportBlockedVodCategories(Number(id)),
    enabled: isAuthenticated && !!id && !!client?.filterId,
  });

  const { data: blockedSeriesCategoriesResponse } = useQuery({
    queryKey: ['blocked-series-categories', id],
    queryFn: () => clientsApi.exportBlockedSeriesCategories(Number(id)),
    enabled: isAuthenticated && !!id && !!client?.filterId,
  });

  // Create mapping of category_id -> category_name for all types (allowed + blocked)
  const categoryNameMap: Record<string, string> = {};
  if (liveCategoriesResponse?.data) {
    (liveCategoriesResponse.data as Category[]).forEach((cat) => {
      categoryNameMap[`live-${cat.category_id}`] = cat.category_name;
    });
  }
  if (blockedLiveCategoriesResponse?.data) {
    (blockedLiveCategoriesResponse.data as Category[]).forEach((cat) => {
      categoryNameMap[`live-${cat.category_id}`] = cat.category_name;
    });
  }
  if (vodCategoriesResponse?.data) {
    (vodCategoriesResponse.data as Category[]).forEach((cat) => {
      categoryNameMap[`vod-${cat.category_id}`] = cat.category_name;
    });
  }
  if (blockedVodCategoriesResponse?.data) {
    (blockedVodCategoriesResponse.data as Category[]).forEach((cat) => {
      categoryNameMap[`vod-${cat.category_id}`] = cat.category_name;
    });
  }
  if (seriesCategoriesResponse?.data) {
    (seriesCategoriesResponse.data as Category[]).forEach((cat) => {
      categoryNameMap[`series-${cat.category_id}`] = cat.category_name;
    });
  }
  if (blockedSeriesCategoriesResponse?.data) {
    (blockedSeriesCategoriesResponse.data as Category[]).forEach((cat) => {
      categoryNameMap[`series-${cat.category_id}`] = cat.category_name;
    });
  }

  // Helper function to enrich streams with category names
  const enrichStreamsWithCategoryNames = (
    streams: Stream[],
    type: string
  ): Stream[] => {
    return streams.map((stream) => ({
      ...stream,
      category_name: categoryNameMap[`${type}-${stream.category_id}`] || `Category ${stream.category_id}`,
    }));
  };

  const { data: allowedResponse, isLoading: allowedLoading } = useQuery({
    queryKey: ['allowed-items', id, selectedAllowedType],
    queryFn: async () => {
      const methodMap: Record<string, (id: number) => Promise<ApiResponse<AllowedItem[]>>> = {
        live_categories: (clientId) => clientsApi.exportLiveCategories(clientId),
        live_streams: (clientId) => clientsApi.exportLiveStreams(clientId),
        vod_categories: (clientId) => clientsApi.exportVodCategories(clientId),
        vod_streams: (clientId) => clientsApi.exportVodStreams(clientId),
        series_categories: (clientId) => clientsApi.exportSeriesCategories(clientId),
        series: (clientId) => clientsApi.exportSeries(clientId),
      };
      if (!selectedAllowedType || !methodMap[selectedAllowedType]) return null;
      const response = await methodMap[selectedAllowedType](Number(id));
      return response;
    },
    enabled: isAuthenticated && !!id && allowedModalOpen && !!selectedAllowedType,
  });

  const { data: blockedResponse, isLoading: blockedLoading } = useQuery({
    queryKey: ['blocked-items', id, selectedBlockedType],
    queryFn: async () => {
      const methodMap: Record<string, (id: number) => Promise<ApiResponse<AllowedItem[]>>> = {
        live_categories: (clientId) => clientsApi.exportBlockedLiveCategories(clientId),
        live_streams: (clientId) => clientsApi.exportBlockedLiveStreams(clientId),
        vod_categories: (clientId) => clientsApi.exportBlockedVodCategories(clientId),
        vod_streams: (clientId) => clientsApi.exportBlockedVodStreams(clientId),
        series_categories: (clientId) => clientsApi.exportBlockedSeriesCategories(clientId),
        series: (clientId) => clientsApi.exportBlockedSeries(clientId),
      };
      if (!selectedBlockedType || !methodMap[selectedBlockedType]) return null;
      const response = await methodMap[selectedBlockedType](Number(id));
      return response;
    },
    enabled: isAuthenticated && !!id && blockedModalOpen && !!selectedBlockedType,
  });

  const source = sourceResponse?.data;
  const blockedData = blockedResponse?.data; // Extract just the data array from response
  const allowedData = allowedResponse?.data; // Extract just the data array from response

  const exportMutation = useMutation({
    mutationFn: ({ exportType }: { exportType: string }) => {
      const methodMap: Record<string, (id: number) => Promise<ApiResponse<AllowedItem[] | Record<string, unknown>>>> = {
        live_categories: (clientId) => clientsApi.exportLiveCategories(clientId),
        live_streams: (clientId) => clientsApi.exportLiveStreams(clientId),
        vod_categories: (clientId) => clientsApi.exportVodCategories(clientId),
        vod_streams: (clientId) => clientsApi.exportVodStreams(clientId),
        series_categories: (clientId) => clientsApi.exportSeriesCategories(clientId),
        series: (clientId) => clientsApi.exportSeries(clientId),
        blocked_live_categories: (clientId) => clientsApi.exportBlockedLiveCategories(clientId),
        blocked_live_streams: (clientId) => clientsApi.exportBlockedLiveStreams(clientId),
        blocked_vod_categories: (clientId) => clientsApi.exportBlockedVodCategories(clientId),
        blocked_vod_streams: (clientId) => clientsApi.exportBlockedVodStreams(clientId),
        blocked_series_categories: (clientId) => clientsApi.exportBlockedSeriesCategories(clientId),
        blocked_series: (clientId) => clientsApi.exportBlockedSeries(clientId),
      };

      return methodMap[exportType](Number(id));
    },
    onMutate: ({ exportType }) => {
      setExportingType(exportType);
      setExportMessage(null);
    },
    onSuccess: (response, { exportType }) => {
      // Trigger download
      const dataStr = JSON.stringify(response.data, null, 2);
      const dataBlob = new Blob([dataStr], { type: 'application/json' });
      const url = URL.createObjectURL(dataBlob);
      const link = document.createElement('a');
      link.href = url;
      link.download = `${exportType}-${id}.json`;
      link.click();
      URL.revokeObjectURL(url);

      setExportMessage({
        type: 'success',
        message: `Exported ${exportType} successfully!`,
      });
      setExportingType(null);
    },
    onError: (error: ApiErrorResponse) => {
      setExportMessage({
        type: 'error',
        message: error?.response?.data?.message || 'Export failed',
      });
      setExportingType(null);
    },
  });

  const handleExport = (exportType: string) => {
    exportMutation.mutate({ exportType });
  };

  const handleViewAllowed = (exportType: string) => {
    setSelectedAllowedType(exportType);
    setAllowedModalOpen(true);
  };

  const handleOpenAllowedInNewTab = async (exportType: string) => {
    try {
      const methodMap: Record<string, (id: number) => Promise<ApiResponse<AllowedItem[]>>> = {
        live_categories: (clientId) => clientsApi.exportLiveCategories(clientId),
        live_streams: (clientId) => clientsApi.exportLiveStreams(clientId),
        vod_categories: (clientId) => clientsApi.exportVodCategories(clientId),
        vod_streams: (clientId) => clientsApi.exportVodStreams(clientId),
        series_categories: (clientId) => clientsApi.exportSeriesCategories(clientId),
        series: (clientId) => clientsApi.exportSeries(clientId),
      };

      const response = await methodMap[exportType](Number(id));
      const jsonStr = JSON.stringify(response.data, null, 2);

      // Create blob and open in new tab
      const blob = new Blob([jsonStr], { type: 'application/json' });
      const url = URL.createObjectURL(blob);
      window.open(url, '_blank');
    } catch (error) {
      console.error('Error opening allowed items:', error);
    }
  };

  const handleViewBlocked = (blockedType: string) => {
    setSelectedBlockedType(blockedType);
    setBlockedModalOpen(true);
  };

  const handleOpenBlockedInNewTab = async (blockedType: string) => {
    try {
      const methodMap: Record<string, (id: number) => Promise<ApiResponse<AllowedItem[]>>> = {
        live_categories: (clientId) => clientsApi.exportBlockedLiveCategories(clientId),
        live_streams: (clientId) => clientsApi.exportBlockedLiveStreams(clientId),
        vod_categories: (clientId) => clientsApi.exportBlockedVodCategories(clientId),
        vod_streams: (clientId) => clientsApi.exportBlockedVodStreams(clientId),
        series_categories: (clientId) => clientsApi.exportBlockedSeriesCategories(clientId),
        series: (clientId) => clientsApi.exportBlockedSeries(clientId),
      };

      const response = await methodMap[blockedType](Number(id));
      const jsonStr = JSON.stringify(response.data, null, 2);

      // Create blob and open in new tab
      const blob = new Blob([jsonStr], { type: 'application/json' });
      const url = URL.createObjectURL(blob);
      window.open(url, '_blank');
    } catch (error) {
      console.error('Error opening blocked items:', error);
    }
  };

  const handleDownloadBlocked = async (blockedType: string) => {
    try {
      const methodMap: Record<string, (id: number) => Promise<ApiResponse<AllowedItem[]>>> = {
        live_categories: (clientId) => clientsApi.exportBlockedLiveCategories(clientId),
        live_streams: (clientId) => clientsApi.exportBlockedLiveStreams(clientId),
        vod_categories: (clientId) => clientsApi.exportBlockedVodCategories(clientId),
        vod_streams: (clientId) => clientsApi.exportBlockedVodStreams(clientId),
        series_categories: (clientId) => clientsApi.exportBlockedSeriesCategories(clientId),
        series: (clientId) => clientsApi.exportBlockedSeries(clientId),
      };

      if (!methodMap[blockedType]) {
        setExportMessage({
          type: 'error',
          message: 'Invalid blocked type',
        });
        return;
      }

      const response = await methodMap[blockedType](Number(id));
      if (!response.data?.data) return;

      const itemsToDownload = response.data.data;
      const filenameMap: Record<string, string> = {
        live_categories: 'blocked-live-categories.json',
        live_streams: 'blocked-live-streams.json',
        vod_categories: 'blocked-vod-categories.json',
        vod_streams: 'blocked-vod-streams.json',
        series_categories: 'blocked-series-categories.json',
        series: 'blocked-series.json',
      };
      const filename = filenameMap[blockedType];

      const dataStr = JSON.stringify(itemsToDownload, null, 2);
      const dataBlob = new Blob([dataStr], { type: 'application/json' });
      const url = URL.createObjectURL(dataBlob);
      const link = document.createElement('a');
      link.href = url;
      link.download = filename;
      link.click();
      URL.revokeObjectURL(url);

      setExportMessage({
        type: 'success',
        message: `Downloaded ${filename}`,
      });
    } catch {
      setExportMessage({
        type: 'error',
        message: 'Failed to download blocked items',
      });
    }
  };

  if (clientLoading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: 400 }}>
        <CircularProgress />
      </Box>
    );
  }

  if (clientError) {
    return (
      <Box sx={{ p: 2 }}>
        <Button startIcon={<ArrowBack />} onClick={() => navigate('/clients')} sx={{ mb: 2 }}>
          Back to Clients
        </Button>
        <Alert severity="error">Failed to load client</Alert>
      </Box>
    );
  }

  if (!client) {
    return (
      <Box sx={{ p: 2 }}>
        <Button startIcon={<ArrowBack />} onClick={() => navigate('/clients')} sx={{ mb: 2 }}>
          Back to Clients
        </Button>
        <Alert severity="warning">Client not found</Alert>
      </Box>
    );
  }

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', height: '100%', width: '100%', flex: 1, p: 2 }}>
      <Box sx={{ mb: 3, display: 'flex', alignItems: 'center', gap: 2 }}>
        <Button startIcon={<ArrowBack />} onClick={() => navigate('/clients')}>
          Back to Clients
        </Button>
        <Typography variant="h5">{client.username}</Typography>
        <Chip
          label={client.isActive ? 'Active' : 'Inactive'}
          color={client.isActive ? 'success' : 'default'}
          size="small"
        />
      </Box>

      <Paper sx={{ p: 3, mb: 3 }}>
        <Typography variant="h6" gutterBottom>
          Client Information
        </Typography>
        <Grid container spacing={2}>
          <Grid item xs={12} md={6}>
            <Typography>
              <strong>Username:</strong> {client.username}
            </Typography>
          </Grid>
          <Grid item xs={12} md={6}>
            <Typography>
              <strong>Email:</strong> {client.email || 'Not set'}
            </Typography>
          </Grid>
          <Grid item xs={12} md={6}>
            <Typography>
              <strong>Source:</strong> {source?.name || 'Unknown'}
            </Typography>
          </Grid>
          <Grid item xs={12} md={6}>
            <Typography>
              <strong>Filter:</strong> {client.filterId ? `Filter #${client.filterId}` : 'None'}
            </Typography>
          </Grid>
          <Grid item xs={12} md={6}>
            <Typography>
              <strong>Created:</strong> {client.createdAt ? new Date(client.createdAt).toLocaleString() : 'Unknown'}
            </Typography>
          </Grid>
          <Grid item xs={12} md={6}>
            <Typography>
              <strong>Status:</strong>{' '}
              <Chip
                label={client.is_active ? 'Active' : 'Inactive'}
                color={client.is_active ? 'success' : 'default'}
                size="small"
              />
            </Typography>
          </Grid>
        </Grid>
      </Paper>

      <Paper sx={{ p: 3, mb: 3 }}>
        <Typography variant="h6" gutterBottom>
          API URLs
        </Typography>
        <Grid container spacing={2}>
          <Grid item xs={12}>
            <Typography variant="body2" color="text.secondary">
              <strong>Base URL:</strong> {`${window.location.origin}/player_api.php`}
            </Typography>
          </Grid>
          <Grid item xs={12}>
            <Typography variant="body2" color="text.secondary" component="div">
              <strong>Authentication:</strong>
              <Typography variant="caption" component="div" sx={{ fontFamily: 'monospace', mt: 1 }}>
                username={client.username}&password={client.password}
              </Typography>
            </Typography>
          </Grid>
        </Grid>
      </Paper>

      <Box sx={{ mb: 3 }}>
        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
          <Typography variant="h6">Export Data</Typography>
        </Box>

        <Grid container spacing={2}>
          {EXPORT_TYPES.map((exportType) => (
            <Grid item xs={12} sm={6} md={exportType.variant === 'warning' ? 12 : 4} key={exportType.id}>
              <Card sx={exportType.variant === 'warning' ? { borderLeft: '4px solid #ff9800' } : {}}>
                <CardContent>
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                    <Typography variant="h6" gutterBottom sx={{ mb: 0 }}>
                      {exportType.label}
                    </Typography>
                    {exportType.variant === 'warning' && <Chip label="Blocked" size="small" color="warning" />}
                  </Box>
                  <Typography variant="body2" color="text.secondary">
                    Download {exportType.label.toLowerCase()} as JSON
                  </Typography>
                </CardContent>
                <Divider />
                <CardActions sx={{ display: 'flex', gap: 1 }}>
                  <Button
                    size="small"
                    startIcon={<ViewIcon />}
                    onClick={() => handleViewAllowed(exportType.id)}
                  >
                    View
                  </Button>
                  <Button
                    size="small"
                    startIcon={<OpenInNewIcon />}
                    onClick={() => handleOpenAllowedInNewTab(exportType.id)}
                  >
                    Open
                  </Button>
                  <Button
                    size="small"
                    startIcon={exportingType === exportType.id ? <CircularProgress size={16} /> : <DownloadIcon />}
                    onClick={() => handleExport(exportType.id)}
                    disabled={!!exportingType}
                  >
                    Download
                  </Button>
                </CardActions>
              </Card>
            </Grid>
          ))}
        </Grid>

        {exportMessage && (
          <Alert severity={exportMessage.type} sx={{ mt: 2 }}>
            {exportMessage.message}
          </Alert>
        )}
      </Box>

      {/* Blocked Items Section */}
      <Box sx={{ mt: 4, pt: 3, borderTop: '1px solid #e0e0e0' }}>
        <Typography variant="h6" gutterBottom sx={{ mb: 2 }}>
          Blocked Items
        </Typography>

        <Grid container spacing={2}>
          {BLOCKED_TYPES.map((blockedType) => (
            <Grid item xs={12} sm={6} md={4} key={blockedType.id}>
              <Card sx={{ borderLeft: '4px solid #ff9800', height: '100%' }}>
                <CardContent>
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                    <Typography variant="h6" gutterBottom sx={{ mb: 0 }}>
                      {blockedType.label}
                    </Typography>
                    <Chip label="Blocked" size="small" color="warning" />
                  </Box>
                  <Typography variant="body2" color="text.secondary">
                    View and download {blockedType.label.toLowerCase()}
                  </Typography>
                </CardContent>
                <Divider />
                <CardActions sx={{ display: 'flex', gap: 1 }}>
                  <Button
                    size="small"
                    startIcon={<BlockIcon />}
                    onClick={() => handleViewBlocked(blockedType.type)}
                  >
                    View
                  </Button>
                  <Button
                    size="small"
                    startIcon={<OpenInNewIcon />}
                    onClick={() => handleOpenBlockedInNewTab(blockedType.type)}
                  >
                    Open
                  </Button>
                  <Button
                    size="small"
                    startIcon={<DownloadIcon />}
                    onClick={() => handleDownloadBlocked(blockedType.type)}
                  >
                    Download
                  </Button>
                </CardActions>
              </Card>
            </Grid>
          ))}
        </Grid>
      </Box>

      {/* Blocked Items Details Modal */}
      <Dialog
        open={blockedModalOpen}
        onClose={() => {
          setBlockedModalOpen(false);
          setBlockedSearch('');
          setBlockedPage(1);
          setBlockedPageSize(20);
        }}
        maxWidth="lg"
        fullWidth
      >
        <DialogTitle>
          {BLOCKED_TYPES.find((b) => b.type === selectedBlockedType)?.label || 'Blocked Items'}
        </DialogTitle>
        <DialogContent>
          {!client?.filterId ? (
            <Alert severity="info" sx={{ mt: 2 }}>
              No filter assigned to this client. All items are accessible.
            </Alert>
          ) : blockedLoading ? (
            <Box sx={{ display: 'flex', justifyContent: 'center', p: 3 }}>
              <CircularProgress />
            </Box>
          ) : (
            <Box sx={{ mt: 2 }}>
              {selectedBlockedType && (
                <>
                  <Stack direction="row" spacing={2} sx={{ mb: 2 }} alignItems="center">
                    <TextField
                      placeholder="Search items..."
                      size="small"
                      value={blockedSearch}
                      onChange={(e) => {
                        setBlockedSearch(e.target.value);
                        setBlockedPage(1);
                      }}
                      sx={{ flex: 1 }}
                    />
                    <FormControl size="small" sx={{ minWidth: 120 }}>
                      <Select
                        value={blockedPageSize}
                        onChange={(e) => {
                          setBlockedPageSize(e.target.value as number);
                          setBlockedPage(1);
                        }}
                      >
                        <MenuItem value={20}>20 per page</MenuItem>
                        <MenuItem value={50}>50 per page</MenuItem>
                        <MenuItem value={100}>100 per page</MenuItem>
                        <MenuItem value={0}>All</MenuItem>
                      </Select>
                    </FormControl>
                  </Stack>
                  {selectedBlockedType.includes('categories') ? (
                    <BlockedCategoriesTable
                      items={(blockedData as Category[]) || []}
                      searchTerm={blockedSearch}
                      pageSize={blockedPageSize}
                      currentPage={blockedPage}
                      onPageChange={setBlockedPage}
                    />
                  ) : (
                    <BlockedStreamsTable
                      items={enrichStreamsWithCategoryNames(
                        (blockedData as Stream[]) || [],
                        selectedBlockedType.replace('_streams', '').replace('blocked_', '')
                      )}
                      searchTerm={blockedSearch}
                      pageSize={blockedPageSize}
                      currentPage={blockedPage}
                      onPageChange={setBlockedPage}
                    />
                  )}
                </>
              )}
            </Box>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => {
            setBlockedModalOpen(false);
            setBlockedSearch('');
            setBlockedPage(1);
            setBlockedPageSize(20);
          }}>Close</Button>
        </DialogActions>
      </Dialog>

      {/* Allowed Items Details Modal */}
      <Dialog
        open={allowedModalOpen}
        onClose={() => {
          setAllowedModalOpen(false);
          setAllowedSearch('');
          setAllowedPage(1);
          setAllowedPageSize(20);
        }}
        maxWidth="lg"
        fullWidth
      >
        <DialogTitle>
          {EXPORT_TYPES.find((e) => e.id === selectedAllowedType)?.label || 'Allowed Items'}
        </DialogTitle>
        <DialogContent>
          {allowedLoading ? (
            <Box sx={{ display: 'flex', justifyContent: 'center', p: 3 }}>
              <CircularProgress />
            </Box>
          ) : allowedData ? (
            <Box sx={{ mt: 2 }}>
              {selectedAllowedType && (
                <>
                  <Stack direction="row" spacing={2} sx={{ mb: 2 }} alignItems="center">
                    <TextField
                      placeholder="Search items..."
                      size="small"
                      value={allowedSearch}
                      onChange={(e) => {
                        setAllowedSearch(e.target.value);
                        setAllowedPage(1);
                      }}
                      sx={{ flex: 1 }}
                    />
                    <FormControl size="small" sx={{ minWidth: 120 }}>
                      <Select
                        value={allowedPageSize}
                        onChange={(e) => {
                          setAllowedPageSize(e.target.value as number);
                          setAllowedPage(1);
                        }}
                      >
                        <MenuItem value={20}>20 per page</MenuItem>
                        <MenuItem value={50}>50 per page</MenuItem>
                        <MenuItem value={100}>100 per page</MenuItem>
                        <MenuItem value={0}>All</MenuItem>
                      </Select>
                    </FormControl>
                  </Stack>
                  {selectedAllowedType.includes('categories') ? (
                    <AllowedCategoriesTable
                      items={(allowedData as Category[]) || []}
                      searchTerm={allowedSearch}
                      pageSize={allowedPageSize}
                      currentPage={allowedPage}
                      onPageChange={setAllowedPage}
                    />
                  ) : (
                    <AllowedStreamsTable
                      items={enrichStreamsWithCategoryNames(
                        (allowedData as Stream[]) || [],
                        selectedAllowedType.replace('_streams', '').replace('_series', '')
                      )}
                      searchTerm={allowedSearch}
                      pageSize={allowedPageSize}
                      currentPage={allowedPage}
                      onPageChange={setAllowedPage}
                    />
                  )}
                </>
              )}
            </Box>
          ) : null}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => {
            setAllowedModalOpen(false);
            setAllowedSearch('');
            setAllowedPage(1);
            setAllowedPageSize(20);
          }}>Close</Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}

interface BlockedCategory {
  category_id: string | number;
  category_name: string;
  parent_id: number | null;
}

interface BlockedStream {
  id: number;
  name: string;
  num?: number;
  category_id: number;
  category_name?: string;
}

function BlockedCategoriesTable({
  items,
  searchTerm = '',
  pageSize = 20,
  currentPage = 1,
  onPageChange,
}: {
  items: BlockedCategory[];
  searchTerm?: string;
  pageSize?: number;
  currentPage?: number;
  onPageChange?: (page: number) => void;
}) {
  const filtered = items.filter((item) =>
    item.category_name.toLowerCase().includes(searchTerm.toLowerCase()) ||
    String(item.category_id).includes(searchTerm)
  );

  const totalPages = pageSize === 0 ? 1 : Math.ceil(filtered.length / pageSize);
  const displayedItems =
    pageSize === 0
      ? filtered
      : filtered.slice((currentPage - 1) * pageSize, currentPage * pageSize);

  return (
    <Box>
      <TableContainer>
        <Table size="small" sx={{ mt: 2 }}>
          <TableHead>
            <TableRow sx={{ backgroundColor: '#f5f5f5' }}>
              <TableCell>Category ID</TableCell>
              <TableCell>Category Name</TableCell>
              <TableCell>Parent ID</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {displayedItems.length === 0 ? (
              <TableRow>
                <TableCell colSpan={3} align="center">
                  {searchTerm ? 'No categories match your search' : 'No blocked categories'}
                </TableCell>
              </TableRow>
            ) : (
              displayedItems.map((item, idx) => (
                <TableRow key={idx}>
                  <TableCell>{item.category_id}</TableCell>
                  <TableCell>{item.category_name}</TableCell>
                  <TableCell>{item.parent_id || '-'}</TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      </TableContainer>
      {pageSize !== 0 && totalPages > 1 && (
        <Box sx={{ display: 'flex', justifyContent: 'center', mt: 2, mb: 2 }}>
          <Pagination
            count={totalPages}
            page={currentPage}
            onChange={(_, page) => onPageChange?.(page)}
          />
        </Box>
      )}
      {searchTerm && (
        <Typography variant="body2" color="textSecondary" sx={{ mt: 1 }}>
          Showing {displayedItems.length} of {filtered.length} items
        </Typography>
      )}
    </Box>
  );
}

function BlockedStreamsTable({
  items,
  searchTerm = '',
  pageSize = 20,
  currentPage = 1,
  onPageChange,
}: {
  items: BlockedStream[];
  searchTerm?: string;
  pageSize?: number;
  currentPage?: number;
  onPageChange?: (page: number) => void;
}) {
  const filtered = items.filter((item) =>
    item.name.toLowerCase().includes(searchTerm.toLowerCase()) ||
    String(item.id).includes(searchTerm) ||
    String(item.category_id).includes(searchTerm) ||
    (item.category_name?.toLowerCase().includes(searchTerm.toLowerCase()) ?? false)
  );

  const totalPages = pageSize === 0 ? 1 : Math.ceil(filtered.length / pageSize);
  const displayedItems =
    pageSize === 0
      ? filtered
      : filtered.slice((currentPage - 1) * pageSize, currentPage * pageSize);

  return (
    <Box>
      <TableContainer>
        <Table size="small" sx={{ mt: 2 }}>
          <TableHead>
            <TableRow sx={{ backgroundColor: '#f5f5f5' }}>
              <TableCell>Stream Name</TableCell>
              <TableCell>Stream Number</TableCell>
              <TableCell>Category ID</TableCell>
              <TableCell>Category Name</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {displayedItems.length === 0 ? (
              <TableRow>
                <TableCell colSpan={4} align="center">
                  {searchTerm ? 'No streams match your search' : 'No blocked streams'}
                </TableCell>
              </TableRow>
            ) : (
              displayedItems.map((item) => (
                <TableRow key={item.id}>
                  <TableCell>{item.name}</TableCell>
                  <TableCell>{item.num || '-'}</TableCell>
                  <TableCell>{item.category_id}</TableCell>
                  <TableCell>{item.category_name || '-'}</TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      </TableContainer>
      {pageSize !== 0 && totalPages > 1 && (
        <Box sx={{ display: 'flex', justifyContent: 'center', mt: 2, mb: 2 }}>
          <Pagination
            count={totalPages}
            page={currentPage}
            onChange={(_, page) => onPageChange?.(page)}
          />
        </Box>
      )}
      {searchTerm && (
        <Typography variant="body2" color="textSecondary" sx={{ mt: 1 }}>
          Showing {displayedItems.length} of {filtered.length} items
        </Typography>
      )}
    </Box>
  );
}

function AllowedCategoriesTable({
  items,
  searchTerm = '',
  pageSize = 20,
  currentPage = 1,
  onPageChange,
}: {
  items: Category[];
  searchTerm?: string;
  pageSize?: number;
  currentPage?: number;
  onPageChange?: (page: number) => void;
}) {
  const filtered = items.filter((item) =>
    item.category_name.toLowerCase().includes(searchTerm.toLowerCase()) ||
    String(item.category_id).includes(searchTerm)
  );

  const totalPages = pageSize === 0 ? 1 : Math.ceil(filtered.length / pageSize);
  const displayedItems =
    pageSize === 0
      ? filtered
      : filtered.slice((currentPage - 1) * pageSize, currentPage * pageSize);

  return (
    <Box>
      <TableContainer>
        <Table size="small" sx={{ mt: 2 }}>
          <TableHead>
            <TableRow sx={{ backgroundColor: '#f5f5f5' }}>
              <TableCell>Category ID</TableCell>
              <TableCell>Category Name</TableCell>
              <TableCell>Parent ID</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {displayedItems.length === 0 ? (
              <TableRow>
                <TableCell colSpan={3} align="center">
                  {searchTerm ? 'No categories match your search' : 'No allowed categories'}
                </TableCell>
              </TableRow>
            ) : (
              displayedItems.map((item, idx) => (
                <TableRow key={idx}>
                  <TableCell>{item.category_id}</TableCell>
                  <TableCell>{item.category_name}</TableCell>
                  <TableCell>{item.parent_id || '-'}</TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      </TableContainer>
      {pageSize !== 0 && totalPages > 1 && (
        <Box sx={{ display: 'flex', justifyContent: 'center', mt: 2, mb: 2 }}>
          <Pagination
            count={totalPages}
            page={currentPage}
            onChange={(_, page) => onPageChange?.(page)}
          />
        </Box>
      )}
      {searchTerm && (
        <Typography variant="body2" color="textSecondary" sx={{ mt: 1 }}>
          Showing {displayedItems.length} of {filtered.length} items
        </Typography>
      )}
    </Box>
  );
}

function AllowedStreamsTable({
  items,
  searchTerm = '',
  pageSize = 20,
  currentPage = 1,
  onPageChange,
}: {
  items: Stream[];
  searchTerm?: string;
  pageSize?: number;
  currentPage?: number;
  onPageChange?: (page: number) => void;
}) {
  const filtered = items.filter((item) =>
    item.name.toLowerCase().includes(searchTerm.toLowerCase()) ||
    String(item.id || '').includes(searchTerm) ||
    String(item.category_id).includes(searchTerm) ||
    (item.category_name?.toLowerCase().includes(searchTerm.toLowerCase()) ?? false)
  );

  const totalPages = pageSize === 0 ? 1 : Math.ceil(filtered.length / pageSize);
  const displayedItems =
    pageSize === 0
      ? filtered
      : filtered.slice((currentPage - 1) * pageSize, currentPage * pageSize);

  return (
    <Box>
      <TableContainer>
        <Table size="small" sx={{ mt: 2 }}>
          <TableHead>
            <TableRow sx={{ backgroundColor: '#f5f5f5' }}>
              <TableCell>Stream Name</TableCell>
              <TableCell>Stream Number</TableCell>
              <TableCell>Category ID</TableCell>
              <TableCell>Category Name</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {displayedItems.length === 0 ? (
              <TableRow>
                <TableCell colSpan={4} align="center">
                  {searchTerm ? 'No streams match your search' : 'No allowed streams'}
                </TableCell>
              </TableRow>
            ) : (
              displayedItems.map((item, idx) => (
                <TableRow key={idx}>
                  <TableCell>{item.name}</TableCell>
                  <TableCell>{item.num || '-'}</TableCell>
                  <TableCell>{item.category_id}</TableCell>
                  <TableCell>{item.category_name || '-'}</TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      </TableContainer>
      {pageSize !== 0 && totalPages > 1 && (
        <Box sx={{ display: 'flex', justifyContent: 'center', mt: 2, mb: 2 }}>
          <Pagination
            count={totalPages}
            page={currentPage}
            onChange={(_, page) => onPageChange?.(page)}
          />
        </Box>
      )}
      {searchTerm && (
        <Typography variant="body2" color="textSecondary" sx={{ mt: 1 }}>
          Showing {displayedItems.length} of {filtered.length} items
        </Typography>
      )}
    </Box>
  );
}
