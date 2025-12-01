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
} from '@mui/material';
import { DataGrid, GridActionsCellItem } from '@mui/x-data-grid';
import type { GridColDef } from '@mui/x-data-grid';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Edit as EditIcon, Delete as DeleteIcon, PlayArrow as SyncIcon } from '@mui/icons-material';
import type { Source } from '../services/sourcesApi';
import sourcesApi from '../services/sourcesApi';
import { useAuthStore } from '../stores/authStore';
import SourceForm from '../components/SourceForm';

export default function Sources() {
  const queryClient = useQueryClient();
  const { isAuthenticated } = useAuthStore();
  const [open, setOpen] = useState(false);
  const [editingSource, setEditingSource] = useState<Source | null>(null);
  const [deleteConfirm, setDeleteConfirm] = useState<number | null>(null);
  const [page, setPage] = useState(1);
  const [limit] = useState(10);

  // Fetch sources (only when authenticated)
  const { data, isLoading, error } = useQuery({
    queryKey: ['sources', page, limit],
    queryFn: () => sourcesApi.getSources(page, limit),
    enabled: isAuthenticated,
  });

  // Delete mutation
  const deleteMutation = useMutation({
    mutationFn: (id: number) => sourcesApi.deleteSource(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['sources'] });
      setDeleteConfirm(null);
    },
  });

  // Sync mutation
  const syncMutation = useMutation({
    mutationFn: (id: number) => sourcesApi.triggerSync(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['sources'] });
    },
  });

  const handleAddSource = () => {
    setEditingSource(null);
    setOpen(true);
  };

  const handleEditSource = (source: Source) => {
    setEditingSource(source);
    setOpen(true);
  };

  const handleCloseForm = () => {
    setOpen(false);
    setEditingSource(null);
  };

  const handleFormSuccess = () => {
    queryClient.invalidateQueries({ queryKey: ['sources'] });
    handleCloseForm();
  };

  const getStatusColor = (
    status: string
  ): 'default' | 'primary' | 'secondary' | 'error' | 'info' | 'success' | 'warning' => {
    switch (status) {
      case 'idle':
        return 'success';
      case 'syncing':
        return 'info';
      case 'error':
        return 'error';
      default:
        return 'default';
    }
  };

  const columns: GridColDef[] = [
    { field: 'id', headerName: 'ID', width: 70 },
    { field: 'name', headerName: 'Name', width: 150, flex: 1 },
    { field: 'url', headerName: 'URL', width: 200, flex: 1 },
    {
      field: 'sync_status',
      headerName: 'Status',
      width: 100,
      renderCell: (params) => (
        <Chip
          label={params.value}
          color={getStatusColor(params.value as string)}
          size="small"
        />
      ),
    },
    {
      field: 'last_sync',
      headerName: 'Last Sync',
      width: 150,
      renderCell: (params) =>
        params.value ? new Date(params.value).toLocaleString() : 'Never',
    },
    {
      field: 'actions',
      type: 'actions',
      width: 120,
      getActions: (params) => [
        <GridActionsCellItem
          key="edit"
          icon={<EditIcon />}
          label="Edit"
          onClick={() => handleEditSource(params.row as Source)}
        />,
        <GridActionsCellItem
          key="sync"
          icon={<SyncIcon />}
          label="Sync"
          onClick={() => syncMutation.mutate(params.row.id)}
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

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', height: '100%', width: '100%', flex: 1, p: 2 }}>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
        <h2 style={{ margin: 0 }}>Sources</h2>
        <Button variant="contained" onClick={handleAddSource}>
          Add Source
        </Button>
      </Box>

      {error && <Alert severity="error" sx={{ mb: 2 }}>Failed to load sources</Alert>}

      <Box sx={{ flex: 1, width: '100%', minHeight: 0 }}>
        <DataGrid
          rows={data?.data || []}
          columns={columns}
          pagination
          paginationModel={{ pageSize: limit, page: page - 1 }}
          onPaginationModelChange={(model) => setPage(model.page + 1)}
          pageSizeOptions={[10]}
          getRowId={(row: Source) => row.id || 0}
          sx={{ height: '100%', width: '100%' }}
        />
      </Box>

      {/* Source Form Modal */}
      <Dialog open={open} onClose={handleCloseForm} maxWidth="sm" fullWidth>
        <SourceForm
          source={editingSource}
          onSuccess={handleFormSuccess}
          onCancel={handleCloseForm}
        />
      </Dialog>

      {/* Delete Confirmation Dialog */}
      <Dialog open={deleteConfirm !== null} onClose={() => setDeleteConfirm(null)}>
        <DialogTitle>Delete Source</DialogTitle>
        <DialogContent>
          Are you sure you want to delete this source? All associated clients and streams will be
          removed.
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
