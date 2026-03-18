import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
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
import { Edit as EditIcon, Delete as DeleteIcon, Visibility as ViewIcon } from '@mui/icons-material';
import type { Client } from '../services/clientsApi';
import clientsApi from '../services/clientsApi';
import sourcesApi from '../services/sourcesApi';
import filtersApi from '../services/filtersApi';
import { useAuthStore } from '../stores/authStore';
import ClientForm from '../components/ClientForm';

interface Source {
  id: number;
  name: string;
}

interface Filter {
  id: number;
  name: string;
}

export default function Clients() {
  const navigate = useNavigate();
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

  // Fetch sources for mapping
  const { data: sourcesData } = useQuery({
    queryKey: ['sources', 1, 100],
    queryFn: () => sourcesApi.getSources(1, 100),
    enabled: isAuthenticated,
  });

  // Fetch filters for mapping
  const { data: filtersData } = useQuery({
    queryKey: ['filters', 1, 100],
    queryFn: () => filtersApi.getFilters(1, 100),
    enabled: isAuthenticated,
  });

  // Create lookup maps
  const sourcesMap = new Map(
    (Array.isArray(sourcesData?.data) ? sourcesData.data : []).map((source: Source) => [
      source.id,
      source.name,
    ])
  );

  const filtersMap = new Map(
    (Array.isArray(filtersData?.data) ? filtersData.data : []).map((filter: Filter) => [
      filter.id,
      filter.name,
    ])
  );

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

  const handleViewClient = (client: Client) => {
    navigate(`/clients/${client.id}`);
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
    { field: 'id', headerName: 'ID', width: 60 },
    { field: 'username', headerName: 'Username', flex: 1, minWidth: 120 },
    {
      field: 'sourceId',
      headerName: 'Source',
      flex: 1,
      minWidth: 120,
      renderCell: (params) => <>{sourcesMap.get(params.value) || '-'}</>,
    },
    {
      field: 'filterId',
      headerName: 'Filter',
      flex: 1,
      minWidth: 120,
      renderCell: (params) => <>{params.value ? filtersMap.get(params.value) || '-' : '-'}</>,
    },
    {
      field: 'isActive',
      headerName: 'Active',
      width: 100,
      renderCell: (params) => (
        <Chip
          label={params.value ? 'Active' : 'Inactive'}
          color={params.value ? 'success' : 'default'}
          size="small"
        />
      ),
    },
    {
      field: 'actions',
      type: 'actions',
      width: 120,
      getActions: (params) => [
        <GridActionsCellItem
          key="view"
          icon={<ViewIcon />}
          label="View"
          onClick={() => handleViewClient(params.row as Client)}
        />,
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
    <Box sx={{ display: 'flex', flexDirection: 'column', height: '100%', width: '100%', flex: 1, p: 2 }}>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
        <h2 style={{ margin: 0 }}>Clients</h2>
        <Button variant="contained" onClick={handleAddClient}>
          Add Client
        </Button>
      </Box>

      <TextField
        placeholder="Search clients..."
        value={search}
        onChange={(e) => {
          setSearch(e.target.value);
          setPage(1);
        }}
        fullWidth
        size="small"
        sx={{ mb: 2 }}
      />

      {error && <Alert severity="error" sx={{ mb: 2 }}>Failed to load clients</Alert>}

      <Box sx={{ flex: 1, width: '100%', minHeight: 0 }}>
        <DataGrid
          rows={data?.data || []}
          columns={columns}
          pagination
          paginationModel={{ pageSize: limit, page: page - 1 }}
          onPaginationModelChange={(model) => setPage(model.page + 1)}
          pageSizeOptions={[10]}
          getRowId={(row: Client) => row.id || 0}
          sx={{ height: '100%', width: '100%' }}
        />
      </Box>

      {/* Client Form Modal */}
      <Dialog open={open} onClose={handleCloseForm} maxWidth="md" fullWidth>
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
