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
} from '@mui/material';
import { DataGrid, GridActionsCellItem } from '@mui/x-data-grid';
import type { GridColDef } from '@mui/x-data-grid';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  Edit as EditIcon,
  Delete as DeleteIcon,
  Preview as PreviewIcon,
  ContentCopy as CloneIcon,
} from '@mui/icons-material';
import type { Filter } from '../services/filtersApi';
import filtersApi from '../services/filtersApi';
import { useAuthStore } from '../stores/authStore';
import FilterForm from '../components/FilterForm';
import FilterPreview from '../components/FilterPreview';

export default function Filters() {
  const queryClient = useQueryClient();
  const { isAuthenticated } = useAuthStore();
  const [open, setOpen] = useState(false);
  const [previewOpen, setPreviewOpen] = useState(false);
  const [editingFilter, setEditingFilter] = useState<Filter | null>(null);
  const [previewFilter, setPreviewFilter] = useState<Filter | null>(null);
  const [deleteConfirm, setDeleteConfirm] = useState<number | null>(null);
  const [page, setPage] = useState(1);
  const [limit] = useState(10);

  // Fetch filters (only when authenticated)
  const { data, isLoading, error } = useQuery({
    queryKey: ['filters', page, limit],
    queryFn: () => filtersApi.getFilters(page, limit),
    enabled: isAuthenticated,
  });

  // Delete mutation
  const deleteMutation = useMutation({
    mutationFn: (id: number) => filtersApi.deleteFilter(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['filters'] });
      setDeleteConfirm(null);
    },
  });

  const handleAddFilter = () => {
    setEditingFilter(null);
    setOpen(true);
  };

  const handleEditFilter = (filter: Filter) => {
    setEditingFilter(filter);
    setOpen(true);
  };

  const handlePreviewFilter = (filter: Filter) => {
    setPreviewFilter(filter);
    setPreviewOpen(true);
  };

  const handleCloneFilter = async (filter: Filter) => {
    try {
      const cloneData = {
        name: `${filter.name} (Copy)`,
        description: filter.description,
        filter_config: filter.filter_config,
      };
      await filtersApi.createFilter(cloneData);
      queryClient.invalidateQueries({ queryKey: ['filters'] });
    } catch (err) {
      console.error('Failed to clone filter:', err);
    }
  };

  const handleCloseForm = () => {
    setOpen(false);
    setEditingFilter(null);
  };

  const handleFormSuccess = () => {
    queryClient.invalidateQueries({ queryKey: ['filters'] });
    handleCloseForm();
  };

  const handleClosePreview = () => {
    setPreviewOpen(false);
    setPreviewFilter(null);
  };

  const columns: GridColDef[] = [
    { field: 'id', headerName: 'ID', width: 70 },
    { field: 'name', headerName: 'Name', width: 200, flex: 1 },
    { field: 'description', headerName: 'Description', width: 250, flex: 1 },
    {
      field: 'created_at',
      headerName: 'Created',
      width: 150,
      renderCell: (params) =>
        params.value ? new Date(params.value).toLocaleString() : '-',
    },
    {
      field: 'actions',
      type: 'actions',
      width: 180,
      getActions: (params) => [
        <GridActionsCellItem
          key="edit"
          icon={<EditIcon />}
          label="Edit"
          onClick={() => handleEditFilter(params.row as Filter)}
        />,
        <GridActionsCellItem
          key="preview"
          icon={<PreviewIcon />}
          label="Preview"
          onClick={() => handlePreviewFilter(params.row as Filter)}
        />,
        <GridActionsCellItem
          key="clone"
          icon={<CloneIcon />}
          label="Clone"
          onClick={() => handleCloneFilter(params.row as Filter)}
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
    <Box sx={{ display: 'flex', flexDirection: 'column', height: '100%', width: '100%', p: 2 }}>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
        <h2 style={{ margin: 0 }}>Filters</h2>
        <Button variant="contained" onClick={handleAddFilter}>
          Add Filter
        </Button>
      </Box>

      {error && <Alert severity="error" sx={{ mb: 2 }}>Failed to load filters</Alert>}

      <Box sx={{ flex: 1, width: '100%', minHeight: 0 }}>
        <DataGrid
          rows={Array.isArray(data?.data) ? data.data : []}
          columns={columns}
          pagination
          paginationModel={{ pageSize: limit, page: page - 1 }}
          onPaginationModelChange={(model) => setPage(model.page + 1)}
          pageSizeOptions={[10]}
          getRowId={(row: Filter) => row.id || 0}
          sx={{ height: '100%', width: '100%' }}
        />
      </Box>

      {/* Filter Form Modal */}
      <Dialog open={open} onClose={handleCloseForm} maxWidth="lg" fullWidth>
        <FilterForm
          filter={editingFilter}
          onSuccess={handleFormSuccess}
          onCancel={handleCloseForm}
        />
      </Dialog>

      {/* Filter Preview Modal */}
      {previewFilter && (
        <FilterPreview
          open={previewOpen}
          filter={previewFilter}
          onClose={handleClosePreview}
        />
      )}

      {/* Delete Confirmation Dialog */}
      <Dialog open={deleteConfirm !== null} onClose={() => setDeleteConfirm(null)}>
        <DialogTitle>Delete Filter</DialogTitle>
        <DialogContent>
          Are you sure you want to delete this filter? This action cannot be undone.
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
