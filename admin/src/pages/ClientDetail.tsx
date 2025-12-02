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
} from '@mui/material';
import { useQuery, useMutation } from '@tanstack/react-query';
import { ArrowBack, Download as DownloadIcon, Block as BlockIcon, OpenInNew as OpenInNewIcon, Visibility as ViewIcon } from '@mui/icons-material';
import clientsApi from '../services/clientsApi';
import sourcesApi from '../services/sourcesApi';
import { useAuthStore } from '../stores/authStore';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';

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

  const { data: clientResponse, isLoading: clientLoading, error: clientError } = useQuery({
    queryKey: ['client', id],
    queryFn: () => clientsApi.getClient(Number(id)),
    enabled: isAuthenticated && !!id,
  });

  const client = clientResponse?.data;

  const { data: sourceResponse } = useQuery({
    queryKey: ['source', client?.source_id],
    queryFn: () => sourcesApi.getSource(Number(client?.source_id)),
    enabled: isAuthenticated && !!client?.source_id,
  });

  const { data: blockedResponse, isLoading: blockedLoading } = useQuery({
    queryKey: ['blocked-items', id],
    queryFn: () => clientsApi.getBlockedItems(Number(id)),
    enabled: isAuthenticated && !!id && blockedModalOpen,
  });

  const { data: allowedResponse, isLoading: allowedLoading } = useQuery({
    queryKey: ['allowed-items', id, selectedAllowedType],
    queryFn: async () => {
      const methodMap: Record<string, (id: number) => Promise<any>> = {
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

  const source = sourceResponse?.data;
  const blockedData = blockedResponse?.data;
  const allowedData = allowedResponse?.data?.data; // Extract just the data array from response

  const exportMutation = useMutation({
    mutationFn: ({ exportType }: { exportType: string }) => {
      const methodMap: Record<string, (id: number) => Promise<any>> = {
        live_categories: (clientId) => clientsApi.exportLiveCategories(clientId),
        live_streams: (clientId) => clientsApi.exportLiveStreams(clientId),
        vod_categories: (clientId) => clientsApi.exportVodCategories(clientId),
        vod_streams: (clientId) => clientsApi.exportVodStreams(clientId),
        series_categories: (clientId) => clientsApi.exportSeriesCategories(clientId),
        series: (clientId) => clientsApi.exportSeries(clientId),
        blocked_items: (clientId) => clientsApi.exportBlockedItems(clientId),
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
    onError: (error: any) => {
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
      const methodMap: Record<string, (id: number) => Promise<any>> = {
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
      const response = await clientsApi.exportBlockedItems(Number(id));
      const blockedItemsData = response.data;

      // Determine which type of data to show based on blockedType
      let items: any[] = [];

      if (blockedType === 'live_categories') {
        items = blockedItemsData.blocked_categories?.live || [];
      } else if (blockedType === 'live_streams') {
        items = blockedItemsData.blocked_streams?.live || [];
      } else if (blockedType === 'vod_categories') {
        items = blockedItemsData.blocked_categories?.vod || [];
      } else if (blockedType === 'vod_streams') {
        items = blockedItemsData.blocked_streams?.vod || [];
      } else if (blockedType === 'series_categories') {
        items = blockedItemsData.blocked_categories?.series || [];
      } else if (blockedType === 'series') {
        items = blockedItemsData.blocked_streams?.series || [];
      }

      const jsonStr = JSON.stringify(items, null, 2);

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
      const response = await clientsApi.exportBlockedItems(Number(id));
      if (!response.data) return;

      const blockedData = response.data;
      let itemsToDownload: any[] = [];
      let filename = '';

      switch (blockedType) {
        case 'live_categories':
          itemsToDownload = blockedData.blocked_categories?.live || [];
          filename = 'blocked-live-categories.json';
          break;
        case 'live_streams':
          itemsToDownload = blockedData.blocked_streams?.live || [];
          filename = 'blocked-live-streams.json';
          break;
        case 'vod_categories':
          itemsToDownload = blockedData.blocked_categories?.vod || [];
          filename = 'blocked-vod-categories.json';
          break;
        case 'vod_streams':
          itemsToDownload = blockedData.blocked_streams?.vod || [];
          filename = 'blocked-vod-streams.json';
          break;
        case 'series_categories':
          itemsToDownload = blockedData.blocked_categories?.series || [];
          filename = 'blocked-series-categories.json';
          break;
        case 'series':
          itemsToDownload = blockedData.blocked_streams?.series || [];
          filename = 'blocked-series.json';
          break;
      }

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
    } catch (error) {
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
          label={client.is_active ? 'Active' : 'Inactive'}
          color={client.is_active ? 'success' : 'default'}
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
              <strong>Filter:</strong> {client.filter_id ? `Filter #${client.filter_id}` : 'None'}
            </Typography>
          </Grid>
          <Grid item xs={12} md={6}>
            <Typography>
              <strong>Created:</strong> {client.created_at ? new Date(client.created_at).toLocaleString() : 'Unknown'}
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
        onClose={() => setBlockedModalOpen(false)}
        maxWidth="lg"
        fullWidth
      >
        <DialogTitle>
          {BLOCKED_TYPES.find((b) => b.type === selectedBlockedType)?.label || 'Blocked Items'}
        </DialogTitle>
        <DialogContent>
          {!client?.filter_id ? (
            <Alert severity="info" sx={{ mt: 2 }}>
              No filter assigned to this client. All items are accessible.
            </Alert>
          ) : blockedLoading ? (
            <Box sx={{ display: 'flex', justifyContent: 'center', p: 3 }}>
              <CircularProgress />
            </Box>
          ) : (
            <Box sx={{ mt: 2 }}>
              {blockedData?.has_filter && selectedBlockedType && (
                <>
                  {selectedBlockedType.includes('categories') ? (
                    <BlockedCategoriesTable
                      items={
                        selectedBlockedType === 'live_categories'
                          ? blockedData.blocked_categories?.live || []
                          : selectedBlockedType === 'vod_categories'
                            ? blockedData.blocked_categories?.vod || []
                            : blockedData.blocked_categories?.series || []
                      }
                    />
                  ) : (
                    <BlockedStreamsTable
                      items={
                        selectedBlockedType === 'live_streams'
                          ? blockedData.blocked_streams?.live || []
                          : selectedBlockedType === 'vod_streams'
                            ? blockedData.blocked_streams?.vod || []
                            : blockedData.blocked_streams?.series || []
                      }
                    />
                  )}
                </>
              )}
            </Box>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setBlockedModalOpen(false)}>Close</Button>
        </DialogActions>
      </Dialog>

      {/* Allowed Items Details Modal */}
      <Dialog
        open={allowedModalOpen}
        onClose={() => setAllowedModalOpen(false)}
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
              {selectedAllowedType && selectedAllowedType.includes('categories') ? (
                <AllowedCategoriesTable items={allowedData as any[] || []} />
              ) : (
                <AllowedStreamsTable items={allowedData as any[] || []} />
              )}
            </Box>
          ) : null}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setAllowedModalOpen(false)}>Close</Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}

interface BlockedCategory {
  id: number;
  category_id: string | number;
  category_name: string;
  parent_id: number;
}

interface BlockedStream {
  id: number;
  name: string;
  num?: number;
  category_id: number;
}

function BlockedCategoriesTable({ items }: { items: BlockedCategory[] }) {
  return (
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
          {items.length === 0 ? (
            <TableRow>
              <TableCell colSpan={3} align="center">
                No blocked categories
              </TableCell>
            </TableRow>
          ) : (
            items.map((item) => (
              <TableRow key={item.id}>
                <TableCell>{item.category_id}</TableCell>
                <TableCell>{item.category_name}</TableCell>
                <TableCell>{item.parent_id}</TableCell>
              </TableRow>
            ))
          )}
        </TableBody>
      </Table>
    </TableContainer>
  );
}

function BlockedStreamsTable({ items }: { items: BlockedStream[] }) {
  return (
    <TableContainer>
      <Table size="small" sx={{ mt: 2 }}>
        <TableHead>
          <TableRow sx={{ backgroundColor: '#f5f5f5' }}>
            <TableCell>Stream Name</TableCell>
            <TableCell>Stream Number</TableCell>
            <TableCell>Category ID</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {items.length === 0 ? (
            <TableRow>
              <TableCell colSpan={3} align="center">
                No blocked streams
              </TableCell>
            </TableRow>
          ) : (
            items.map((item) => (
              <TableRow key={item.id}>
                <TableCell>{item.name}</TableCell>
                <TableCell>{item.num || '-'}</TableCell>
                <TableCell>{item.category_id}</TableCell>
              </TableRow>
            ))
          )}
        </TableBody>
      </Table>
    </TableContainer>
  );
}

function AllowedCategoriesTable({ items }: { items: any[] }) {
  return (
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
          {items.length === 0 ? (
            <TableRow>
              <TableCell colSpan={3} align="center">
                No allowed categories
              </TableCell>
            </TableRow>
          ) : (
            items.map((item, idx) => (
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
  );
}

function AllowedStreamsTable({ items }: { items: any[] }) {
  return (
    <TableContainer>
      <Table size="small" sx={{ mt: 2 }}>
        <TableHead>
          <TableRow sx={{ backgroundColor: '#f5f5f5' }}>
            <TableCell>Stream Name</TableCell>
            <TableCell>Stream Number</TableCell>
            <TableCell>Category ID</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {items.length === 0 ? (
            <TableRow>
              <TableCell colSpan={3} align="center">
                No allowed streams
              </TableCell>
            </TableRow>
          ) : (
            items.map((item, idx) => (
              <TableRow key={idx}>
                <TableCell>{item.name}</TableCell>
                <TableCell>{item.num || '-'}</TableCell>
                <TableCell>{item.category_id}</TableCell>
              </TableRow>
            ))
          )}
        </TableBody>
      </Table>
    </TableContainer>
  );
}
