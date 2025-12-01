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
import { Edit as EditIcon, Delete as DeleteIcon } from '@mui/icons-material';
import type { AdminUser } from '../services/adminUsersApi';
import adminUsersApi from '../services/adminUsersApi';
import { useAuthStore } from '../stores/authStore';
import AdminUserForm from '../components/AdminUserForm';

export default function AdminUsers() {
  const queryClient = useQueryClient();
  const { isAuthenticated, user: currentUser } = useAuthStore();
  const [open, setOpen] = useState(false);
  const [editingUser, setEditingUser] = useState<AdminUser | null>(null);
  const [deleteConfirm, setDeleteConfirm] = useState<number | null>(null);
  const [page, setPage] = useState(1);
  const [limit] = useState(10);

  // Fetch admin users (only when authenticated)
  const { data, isLoading, error } = useQuery({
    queryKey: ['adminUsers', page, limit],
    queryFn: () => adminUsersApi.getAdminUsers(page, limit),
    enabled: isAuthenticated,
  });

  // Delete mutation
  const deleteMutation = useMutation({
    mutationFn: (id: number) => adminUsersApi.deleteAdminUser(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['adminUsers'] });
      setDeleteConfirm(null);
    },
  });

  const handleAddUser = () => {
    setEditingUser(null);
    setOpen(true);
  };

  const handleEditUser = (user: AdminUser) => {
    setEditingUser(user);
    setOpen(true);
  };

  const handleCloseForm = () => {
    setOpen(false);
    setEditingUser(null);
  };

  const handleFormSuccess = () => {
    queryClient.invalidateQueries({ queryKey: ['adminUsers'] });
    handleCloseForm();
  };

  const handleDeleteClick = (userId: number) => {
    // Prevent self-deletion
    if (currentUser?.id === userId) {
      alert('You cannot delete your own account');
      return;
    }
    setDeleteConfirm(userId);
  };

  const columns: GridColDef[] = [
    { field: 'id', headerName: 'ID', width: 60 },
    { field: 'username', headerName: 'Username', flex: 1, minWidth: 150 },
    { field: 'email', headerName: 'Email', flex: 1, minWidth: 200 },
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
      field: 'created_at',
      headerName: 'Created',
      width: 150,
      renderCell: (params) =>
        params.value ? new Date(params.value).toLocaleString() : '-',
    },
    {
      field: 'actions',
      type: 'actions',
      width: 120,
      getActions: (params) => {
        const userId = params.row.id;
        const isSelf = currentUser?.id === userId;

        return [
          <GridActionsCellItem
            key="edit"
            icon={<EditIcon />}
            label="Edit"
            onClick={() => handleEditUser(params.row as AdminUser)}
          />,
          <GridActionsCellItem
            key="delete"
            icon={<DeleteIcon />}
            label="Delete"
            disabled={isSelf}
            onClick={() => handleDeleteClick(userId)}
            showInMenu={false}
            sx={isSelf ? { opacity: 0.5, cursor: 'not-allowed' } : {}}
          />,
        ];
      },
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
        <h2 style={{ margin: 0 }}>Admin Users</h2>
        <Button variant="contained" onClick={handleAddUser}>
          Add Admin User
        </Button>
      </Box>

      {error && <Alert severity="error" sx={{ mb: 2 }}>Failed to load admin users</Alert>}

      <Box sx={{ flex: 1, width: '100%', minHeight: 0 }}>
        <DataGrid
          rows={Array.isArray(data?.data) ? data.data : []}
          columns={columns}
          pagination
          paginationModel={{ pageSize: limit, page: page - 1 }}
          onPaginationModelChange={(model) => setPage(model.page + 1)}
          pageSizeOptions={[10]}
          getRowId={(row: AdminUser) => row.id || 0}
          sx={{ height: '100%', width: '100%' }}
        />
      </Box>

      {/* Admin User Form Modal */}
      <Dialog open={open} onClose={handleCloseForm} maxWidth="sm" fullWidth>
        <AdminUserForm
          user={editingUser}
          onSuccess={handleFormSuccess}
          onCancel={handleCloseForm}
        />
      </Dialog>

      {/* Delete Confirmation Dialog */}
      <Dialog open={deleteConfirm !== null} onClose={() => setDeleteConfirm(null)}>
        <DialogTitle>Delete Admin User</DialogTitle>
        <DialogContent>
          Are you sure you want to delete this admin user? This action cannot be undone.
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
