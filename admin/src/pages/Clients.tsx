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
  TextField,
} from '@mui/material';
import { DataGrid, GridActionsCellItem } from '@mui/x-data-grid';
import type { GridColDef } from '@mui/x-data-grid';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Edit as EditIcon, Delete as DeleteIcon } from '@mui/icons-material';
import type { Client } from '../services/clientsApi';
import clientsApi from '../services/clientsApi';
import { useAuthStore } from '../stores/authStore';
import ClientForm from '../components/ClientForm';

export default function Clients() {
  const queryClient = useQueryClient();
  const { isAuthenticated } = useAuthStore();
  const [open, setOpen] = useState(false);
  const [editingClient, setEditingClient] = useState<Client | null>(null);
  const [deleteConfirm, setDeleteConfirm] = useState<number | null>(null);
  const [page, setPage] = useState(1);
  const [limit] = useState(10);
  const [search, setSearch] = useState('');

  // Fetch clients (only when authenticated)
  const { data, isLoading, error } = useQuery({
    queryKey: ['clients', page, limit, search],
    queryFn: () => clientsApi.getClients(page, limit, search),
    enabled: isAuthenticated,
  });

  // Delete mutation
  const deleteMutation = useMutation({
    mutationFn: (id: number) => clientsApi.deleteClient(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['clients'] });
      setDeleteConfirm(null);
    },
  });

  const handleAddClient = () => {
    setEditingClient(null);
    setOpen(true);
  };

  const handleEditClient = (client: Client) => {
    setEditingClient(client);
    setOpen(true);
  };

  const handleCloseForm = () => {
    setOpen(false);
    setEditingClient(null);
  };

  const handleFormSuccess = () => {
    queryClient.invalidateQueries({ queryKey: ['clients'] });
    handleCloseForm();
  };

  const columns: GridColDef[] = [
    { field: 'id', headerName: 'ID', width: 70 },
    { field: 'username', headerName: 'Username', width: 150, flex: 1 },
    { field: 'source_id', headerName: 'Source ID', width: 100 },
    { field: 'email', headerName: 'Email', width: 200, flex: 1 },
    {
      field: 'is_active',
      headerName: 'Active',
      width: 100,
      renderCell: (params) => (
        <Chip
          label={params.value === 1 ? 'Active' : 'Inactive'}
          color={params.value === 1 ? 'success' : 'default'}
          size="small"
        />
      ),
    },
    {
      field: 'actions',
      type: 'actions',
      width: 100,
      getActions: (params) => [
        <GridActionsCellItem
          key="edit"
          icon={<EditIcon />}
          label="Edit"
          onClick={() => handleEditClient(params.row as Client)}
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
    <Box>
      <Box sx={{ mb: 3, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <h2>Clients</h2>
        <Button variant="contained" onClick={handleAddClient}>
          Add Client
        </Button>
      </Box>

      <Box sx={{ mb: 2 }}>
        <TextField
          placeholder="Search clients..."
          value={search}
          onChange={(e) => {
            setSearch(e.target.value);
            setPage(1);
          }}
          fullWidth
          size="small"
        />
      </Box>

      {error && <Alert severity="error">Failed to load clients</Alert>}

      <Box sx={{ height: 600, width: '100%' }}>
        <DataGrid
          rows={data?.data || []}
          columns={columns}
          pagination
          paginationModel={{ pageSize: limit, page: page - 1 }}
          onPaginationModelChange={(model) => setPage(model.page + 1)}
          pageSizeOptions={[10]}
          getRowId={(row: Client) => row.id || 0}
        />
      </Box>

      {/* Client Form Modal */}
      <Dialog open={open} onClose={handleCloseForm} maxWidth="sm" fullWidth>
        <ClientForm
          client={editingClient}
          onSuccess={handleFormSuccess}
          onCancel={handleCloseForm}
        />
      </Dialog>

      {/* Delete Confirmation Dialog */}
      <Dialog open={deleteConfirm !== null} onClose={() => setDeleteConfirm(null)}>
        <DialogTitle>Delete Client</DialogTitle>
        <DialogContent>
          Are you sure you want to delete this client? They will no longer be able to access the
          streams.
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
