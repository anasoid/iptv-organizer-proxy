import { useState } from 'react';
import {
  Box,
  Button,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  CircularProgress,
  Alert,
  Chip,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  Grid,
  Card,
  CardContent,
  Typography,
} from '@mui/material';
import { DataGrid, GridActionsCellItem } from '@mui/x-data-grid';
import type { GridColDef } from '@mui/x-data-grid';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Delete as DeleteIcon, Visibility as ViewIcon, Refresh as RefreshIcon } from '@mui/icons-material';
import syncLogsApi, { SYNC_TYPES } from '../services/syncLogsApi';
import type { SyncLog } from '../services/syncLogsApi';
import sourcesApi from '../services/sourcesApi';
import type { Source } from '../services/sourcesApi';
import { useAuthStore } from '../stores/authStore';

export default function SyncLogs() {
  const queryClient = useQueryClient();
  const { isAuthenticated } = useAuthStore();
  const [deleteConfirm, setDeleteConfirm] = useState<number | null>(null);
  const [viewLog, setViewLog] = useState<SyncLog | null>(null);
  const [page, setPage] = useState(1);
  const [limit] = useState(10);

  // Filters
  const [sourceIdFilter, setSourceIdFilter] = useState<number | ''>('');
  const [syncTypeFilter, setSyncTypeFilter] = useState<string>('');
  const [statusFilter, setStatusFilter] = useState<string>('');

  // Sorting
  const [sortModel, setSortModel] = useState([{ field: 'startedAt', sort: 'desc' }]);

  // Build filters object
  const filters = {
    ...(sourceIdFilter ? { sourceId: sourceIdFilter } : {}),
    ...(syncTypeFilter ? { syncType: syncTypeFilter } : {}),
    ...(statusFilter ? { status: statusFilter } : {}),
  };

  // Extract sort parameters
  const sortBy = sortModel[0]?.field || 'startedAt';
  const sortOrder = sortModel[0]?.sort === 'desc' ? 'desc' : 'asc';

  // Fetch sync logs (only when authenticated)
  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['syncLogs', page, limit, filters, sortBy, sortOrder],
    queryFn: () => syncLogsApi.getSyncLogs(page, limit, filters, sortBy, sortOrder),
    enabled: isAuthenticated,
  });

  // Fetch sources for filter dropdown
  const { data: sourcesData } = useQuery({
    queryKey: ['sources', 1, 1000],
    queryFn: () => sourcesApi.getSources(1, 1000),
    enabled: isAuthenticated,
  });

  // Fetch statistics
  const { data: statsData } = useQuery({
    queryKey: ['syncLogStats', filters],
    queryFn: () => syncLogsApi.getSyncLogStats(filters),
    enabled: isAuthenticated,
  });

  // Delete mutation
  const deleteMutation = useMutation({
    mutationFn: (id: number) => syncLogsApi.deleteSyncLog(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['syncLogs'] });
      queryClient.invalidateQueries({ queryKey: ['syncLogStats'] });
      setDeleteConfirm(null);
    },
  });

  const handleClearFilters = () => {
    setSourceIdFilter('');
    setSyncTypeFilter('');
    setStatusFilter('');
  };

  const getStatusColor = (
    status: string
  ): 'default' | 'primary' | 'secondary' | 'error' | 'info' | 'success' | 'warning' => {
    switch (status) {
      case 'completed':
        return 'success';
      case 'running':
        return 'info';
      case 'failed':
        return 'error';
      default:
        return 'default';
    }
  };

  const formatDuration = (seconds: number | null | undefined): string => {
    if (seconds === null || seconds === undefined) return '-';
    if (seconds < 60) return `${seconds}s`;
    const minutes = Math.floor(seconds / 60);
    const remainingSeconds = seconds % 60;
    return `${minutes}m ${remainingSeconds}s`;
  };

  const columns: GridColDef[] = [
    { field: 'id', headerName: 'ID', width: 70 },
    {
      field: 'sourceName',
      headerName: 'Source',
      width: 150,
      flex: 1,
      renderCell: (params) => params.value || `Source #${params.row.sourceId}`,
    },
    {
      field: 'syncType',
      headerName: 'Type',
      width: 150,
      renderCell: (params) => {
        const typeObj = SYNC_TYPES.find(t => t.id === params.value);
        return typeObj?.label || params.value;
      },
    },
    {
      field: 'status',
      headerName: 'Status',
      width: 110,
      renderCell: (params) => (
        <Chip
          label={params.value}
          color={getStatusColor(params.value as string)}
          size="small"
        />
      ),
    },
    {
      field: 'startedAt',
      headerName: 'Started',
      width: 160,
      renderCell: (params) =>
        params.value ? new Date(params.value).toLocaleString() : '-',
    },
    {
      field: 'durationSeconds',
      headerName: 'Duration',
      width: 90,
      renderCell: (params) => formatDuration(params.value),
    },
    {
      field: 'itemsAdded',
      headerName: 'Added',
      width: 80,
      align: 'right',
      headerAlign: 'right',
    },
    {
      field: 'itemsUpdated',
      headerName: 'Updated',
      width: 80,
      align: 'right',
      headerAlign: 'right',
    },
    {
      field: 'itemsDeleted',
      headerName: 'Deleted',
      width: 80,
      align: 'right',
      headerAlign: 'right',
    },
    {
      field: 'actions',
      type: 'actions',
      width: 80,
      getActions: (params) => [
        <GridActionsCellItem
          key="view"
          icon={<ViewIcon />}
          label="View Details"
          onClick={() => setViewLog(params.row as SyncLog)}
        />,
        <GridActionsCellItem
          key="delete"
          icon={<DeleteIcon />}
          label="Delete"
          onClick={() => setDeleteConfirm(params.row.id)}
        />,
      ],
    },
  ];

  if (isLoading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: 400 }}>
        <CircularProgress />
      </Box>
    );
  }

  const stats = statsData?.data;

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', height: '100%', width: '100%', flex: 1, p: 2 }}>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
        <h2 style={{ margin: 0 }}>Sync Logs</h2>
        <Button
          variant="outlined"
          startIcon={<RefreshIcon />}
          onClick={() => refetch()}
        >
          Refresh
        </Button>
      </Box>

      {error && <Alert severity="error" sx={{ mb: 2 }}>Failed to load sync logs</Alert>}

      {/* Statistics Cards */}
      {stats && (
        <Grid container spacing={2} sx={{ mb: 2 }}>
          <Grid item xs={12} sm={6} md={4}>
            <Card>
              <CardContent>
                <Typography color="textSecondary" gutterBottom variant="body2">
                  Total Syncs
                </Typography>
                <Typography variant="h5">
                  {stats.totalSyncs || 0}
                </Typography>
              </CardContent>
            </Card>
          </Grid>
          <Grid item xs={12} sm={6} md={4}>
            <Card>
              <CardContent>
                <Typography color="textSecondary" gutterBottom variant="body2">
                  Completed
                </Typography>
                <Typography variant="h5" color="success.main">
                  {stats.completedSyncs || 0}
                </Typography>
              </CardContent>
            </Card>
          </Grid>
          <Grid item xs={12} sm={6} md={4}>
            <Card>
              <CardContent>
                <Typography color="textSecondary" gutterBottom variant="body2">
                  Failed
                </Typography>
                <Typography variant="h5" color="error.main">
                  {stats.failedSyncs || 0}
                </Typography>
              </CardContent>
            </Card>
          </Grid>
        </Grid>
      )}

      {/* Filters */}
      <Box sx={{ mb: 2, display: 'flex', gap: 2, flexWrap: 'wrap' }}>
        <FormControl size="small" sx={{ minWidth: 200 }}>
          <InputLabel>Source</InputLabel>
          <Select
            value={sourceIdFilter}
            label="Source"
            onChange={(e) => setSourceIdFilter(e.target.value as number | '')}
          >
            <MenuItem value="">All Sources</MenuItem>
            {sourcesData?.data?.map((source: Source) => (
              <MenuItem key={source.id} value={source.id}>
                {source.name}
              </MenuItem>
            ))}
          </Select>
        </FormControl>

        <FormControl size="small" sx={{ minWidth: 200 }}>
          <InputLabel>Sync Type</InputLabel>
          <Select
            value={syncTypeFilter}
            label="Sync Type"
            onChange={(e) => setSyncTypeFilter(e.target.value)}
          >
            <MenuItem value="">All Types</MenuItem>
            {SYNC_TYPES.map((type) => (
              <MenuItem key={type.id} value={type.id}>
                {type.label}
              </MenuItem>
            ))}
          </Select>
        </FormControl>

        <FormControl size="small" sx={{ minWidth: 150 }}>
          <InputLabel>Status</InputLabel>
          <Select
            value={statusFilter}
            label="Status"
            onChange={(e) => setStatusFilter(e.target.value)}
          >
            <MenuItem value="">All Status</MenuItem>
            <MenuItem value="running">Running</MenuItem>
            <MenuItem value="completed">Completed</MenuItem>
            <MenuItem value="failed">Failed</MenuItem>
          </Select>
        </FormControl>

        {(sourceIdFilter || syncTypeFilter || statusFilter) && (
          <Button variant="outlined" onClick={handleClearFilters}>
            Clear Filters
          </Button>
        )}
      </Box>

      <Box sx={{ flex: 1, width: '100%', minHeight: 0 }}>
        <DataGrid
          rows={data?.data || []}
          columns={columns}
          pagination
          paginationMode="server"
          sortingMode="server"
          rowCount={data?.pagination?.total || 0}
          paginationModel={{ pageSize: limit, page: page - 1 }}
          onPaginationModelChange={(model) => setPage(model.page + 1)}
          sortModel={sortModel}
          onSortModelChange={(newSortModel) => setSortModel(newSortModel)}
          pageSizeOptions={[10]}
          getRowId={(row: SyncLog) => row.id}
          sx={{ height: '100%', width: '100%' }}
        />
      </Box>

      {/* View Details Dialog */}
      <Dialog
        open={viewLog !== null}
        onClose={() => setViewLog(null)}
        maxWidth="md"
        fullWidth
      >
        <DialogTitle>Sync Log Details</DialogTitle>
        <DialogContent>
          {viewLog && (
            <Box sx={{ pt: 1 }}>
              <Grid container spacing={2}>
                <Grid item xs={6}>
                  <Typography variant="body2" color="textSecondary">
                    ID
                  </Typography>
                  <Typography variant="body1">{viewLog.id}</Typography>
                </Grid>
                <Grid item xs={6}>
                  <Typography variant="body2" color="textSecondary">
                    Source
                  </Typography>
                  <Typography variant="body1">
                    {viewLog.sourceName || `Source #${viewLog.sourceId}`}
                  </Typography>
                </Grid>
                <Grid item xs={6}>
                  <Typography variant="body2" color="textSecondary">
                    Sync Type
                  </Typography>
                  <Typography variant="body1">
                    {SYNC_TYPES.find(t => t.id === viewLog.syncType)?.label || viewLog.syncType}
                  </Typography>
                </Grid>
                <Grid item xs={6}>
                  <Typography variant="body2" color="textSecondary">
                    Status
                  </Typography>
                  <Chip
                    label={viewLog.status}
                    color={getStatusColor(viewLog.status)}
                    size="small"
                  />
                </Grid>
                <Grid item xs={6}>
                  <Typography variant="body2" color="textSecondary">
                    Started At
                  </Typography>
                  <Typography variant="body1">
                    {new Date(viewLog.startedAt).toLocaleString()}
                  </Typography>
                </Grid>
                <Grid item xs={6}>
                  <Typography variant="body2" color="textSecondary">
                    Completed At
                  </Typography>
                  <Typography variant="body1">
                    {viewLog.completedAt ? new Date(viewLog.completedAt).toLocaleString() : '-'}
                  </Typography>
                </Grid>
                <Grid item xs={6}>
                  <Typography variant="body2" color="textSecondary">
                    Duration
                  </Typography>
                  <Typography variant="body1">
                    {formatDuration(viewLog.durationSeconds)}
                  </Typography>
                </Grid>
                <Grid item xs={6}>
                  <Typography variant="body2" color="textSecondary">
                    Items Added
                  </Typography>
                  <Typography variant="body1">{viewLog.itemsAdded}</Typography>
                </Grid>
                <Grid item xs={6}>
                  <Typography variant="body2" color="textSecondary">
                    Items Updated
                  </Typography>
                  <Typography variant="body1">{viewLog.itemsUpdated}</Typography>
                </Grid>
                <Grid item xs={6}>
                  <Typography variant="body2" color="textSecondary">
                    Items Deleted
                  </Typography>
                  <Typography variant="body1">{viewLog.itemsDeleted}</Typography>
                </Grid>
                {viewLog.errorMessage && (
                  <Grid item xs={12}>
                    <Typography variant="body2" color="textSecondary">
                      Error Message
                    </Typography>
                    <Alert severity="error" sx={{ mt: 1 }}>
                      {viewLog.errorMessage}
                    </Alert>
                  </Grid>
                )}
              </Grid>
            </Box>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setViewLog(null)}>Close</Button>
        </DialogActions>
      </Dialog>

      {/* Delete Confirmation Dialog */}
      <Dialog open={deleteConfirm !== null} onClose={() => setDeleteConfirm(null)}>
        <DialogTitle>Delete Sync Log</DialogTitle>
        <DialogContent>
          Are you sure you want to delete this sync log? This action cannot be undone.
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDeleteConfirm(null)}>Cancel</Button>
          <Button
            onClick={() => deleteConfirm && deleteMutation.mutate(deleteConfirm)}
            color="error"
            variant="contained"
          >
            Delete
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}
