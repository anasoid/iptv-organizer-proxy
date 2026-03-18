import { useState } from 'react';
import {
  Box,
  Button,
  Dialog,
  CircularProgress,
  Alert,
  Chip,
} from '@mui/material';
import { DataGrid, GridActionsCellItem } from '@mui/x-data-grid';
import type { GridColDef } from '@mui/x-data-grid';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Edit as EditIcon, Delete as DeleteIcon } from '@mui/icons-material';
import type { Proxy } from '../services/proxiesApi';
import proxiesApi from '../services/proxiesApi';
import { useAuthStore } from '../stores/authStore';
import ProxyForm from '../components/ProxyForm';

export default function Proxies() {
  const queryClient = useQueryClient();
  const { isAuthenticated } = useAuthStore();
  const [open, setOpen] = useState(false);
  const [editingProxy, setEditingProxy] = useState<Proxy | null>(null);
  const [deleteConfirm, setDeleteConfirm] = useState<number | null>(null);
  const [page, setPage] = useState(1);
  const [limit] = useState(10);

  // Fetch proxies (only when authenticated)
  const { data, isLoading, error } = useQuery({
    queryKey: ['proxies', page, limit],
    queryFn: () => proxiesApi.getProxies(page, limit),
    enabled: isAuthenticated,
  });

  // Delete mutation
  const deleteMutation = useMutation({
    mutationFn: (id: number) => proxiesApi.deleteProxy(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['proxies'] });
      setDeleteConfirm(null);
    },
  });

  const handleAddProxy = () => {
    setEditingProxy(null);
    setOpen(true);
  };

  const handleEditProxy = (proxy: Proxy) => {
    setEditingProxy(proxy);
    setOpen(true);
  };

  const handleCloseForm = () => {
    setOpen(false);
    setEditingProxy(null);
  };

  const handleFormSuccess = () => {
    queryClient.invalidateQueries({ queryKey: ['proxies'] });
    handleCloseForm();
  };

  const handleDeleteClick = (proxyId: number) => {
    setDeleteConfirm(proxyId);
  };

  const handleConfirmDelete = () => {
    if (deleteConfirm !== null) {
      deleteMutation.mutate(deleteConfirm);
    }
  };

  const columns: GridColDef[] = [
    { field: 'id', headerName: 'ID', width: 60 },
    { field: 'name', headerName: 'Name', flex: 1, minWidth: 150 },
    { field: 'description', headerName: 'Description', flex: 1, minWidth: 200 },
    { field: 'proxyHost', headerName: 'Host', flex: 1, minWidth: 150 },
    { field: 'proxyPort', headerName: 'Port', width: 80 },
    {
      field: 'proxyType',
      headerName: 'Type',
      width: 100,
      renderCell: (params) => {
        const type = params.value;
        if (!type) return '-';
        const colorMap: Record<string, 'default' | 'primary' | 'secondary' | 'error' | 'warning' | 'info' | 'success'> = {
          HTTP: 'info',
          HTTPS: 'success',
          SOCKS5: 'warning',
        };
        return (
          <Chip
            label={type}
            size="small"
            color={colorMap[type] || 'default'}
          />
        );
      },
    },
    {
      field: 'createdAt',
      headerName: 'Created',
      width: 150,
      renderCell: (params) =>
        params.value ? new Date(params.value).toLocaleString() : '-',
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
          onClick={() => handleEditProxy(params.row as Proxy)}
        />,
        <GridActionsCellItem
          key="delete"
          icon={<DeleteIcon />}
          label="Delete"
          onClick={() => handleDeleteClick(params.row.id)}
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
    <Box sx={{ display: 'flex', flexDirection: 'column', height: '100%', width: '100%', p: 2 }}>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
        <h2 style={{ margin: 0 }}>Proxies</h2>
        <Button variant="contained" onClick={handleAddProxy}>
          Add Proxy
        </Button>
      </Box>

      {error && <Alert severity="error" sx={{ mb: 2 }}>Failed to load proxies</Alert>}

      <Box sx={{ flex: 1, width: '100%', minHeight: 0 }}>
        <DataGrid
          rows={Array.isArray(data?.data) ? data.data : []}
          columns={columns}
          pagination
          paginationModel={{ pageSize: limit, page: page - 1 }}
          onPaginationModelChange={(model) => setPage(model.page + 1)}
          pageSizeOptions={[10]}
          rowCount={data?.pagination?.total || 0}
          paginationMode="server"
        />
      </Box>

      {/* Add/Edit Dialog */}
      <Dialog
        open={open}
        onClose={handleCloseForm}
        maxWidth="sm"
        fullWidth
      >
        <ProxyForm
          proxy={editingProxy}
          onSuccess={handleFormSuccess}
          onCancel={handleCloseForm}
        />
      </Dialog>

      {/* Delete Confirmation Dialog */}
      <Dialog
        open={deleteConfirm !== null}
        onClose={() => setDeleteConfirm(null)}
      >
        <Box sx={{ p: 3, minWidth: 300 }}>
          <h3>Confirm Delete</h3>
          <p>Are you sure you want to delete this proxy?</p>
          <Box sx={{ display: 'flex', gap: 1, justifyContent: 'flex-end' }}>
            <Button onClick={() => setDeleteConfirm(null)}>
              Cancel
            </Button>
            <Button
              onClick={handleConfirmDelete}
              variant="contained"
              color="error"
              disabled={deleteMutation.isPending}
            >
              {deleteMutation.isPending ? <CircularProgress size={24} /> : 'Delete'}
            </Button>
          </Box>
        </Box>
      </Dialog>
    </Box>
  );
}
