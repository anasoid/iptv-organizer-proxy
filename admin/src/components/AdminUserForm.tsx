import { useState } from 'react';
import {
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
  Button,
  Alert,
  FormControlLabel,
  Checkbox,
  CircularProgress,
} from '@mui/material';
import { useMutation } from '@tanstack/react-query';
import type { AdminUser } from '../services/adminUsersApi';
import adminUsersApi from '../services/adminUsersApi';

interface AdminUserFormProps {
  user: AdminUser | null;
  onSuccess: () => void;
  onCancel: () => void;
}

export default function AdminUserForm({ user, onSuccess, onCancel }: AdminUserFormProps) {
  const [username, setUsername] = useState(user?.username || '');
  const [email, setEmail] = useState(user?.email || '');
  const [password, setPassword] = useState('');
  const [isActive, setIsActive] = useState(user?.is_active === 1 || !user);
  const [error, setError] = useState<string | null>(null);

  const createMutation = useMutation({
    mutationFn: (data: Parameters<typeof adminUsersApi.createAdminUser>[0]) =>
      adminUsersApi.createAdminUser(data),
    onSuccess: () => {
      onSuccess();
    },
    onError: (err) => {
      setError(err instanceof Error ? err.message : 'Failed to create admin user');
    },
  });

  const updateMutation = useMutation({
    mutationFn: (data: Parameters<typeof adminUsersApi.updateAdminUser>[1]) =>
      adminUsersApi.updateAdminUser(user!.id, data),
    onSuccess: () => {
      onSuccess();
    },
    onError: (err) => {
      setError(err instanceof Error ? err.message : 'Failed to update admin user');
    },
  });

  const handleSubmit = async () => {
    // Validation
    if (!username.trim()) {
      setError('Username is required');
      return;
    }

    if (!user && !password.trim()) {
      setError('Password is required for new admin users');
      return;
    }

    if (email && !email.match(/^[^\s@]+@[^\s@]+\.[^\s@]+$/)) {
      setError('Invalid email format');
      return;
    }

    setError(null);

    if (user) {
      // Update mode
      updateMutation.mutate({
        username: username.trim(),
        email: email.trim() || undefined,
        is_active: isActive ? 1 : 0,
      });
    } else {
      // Create mode
      createMutation.mutate({
        username: username.trim(),
        password: password.trim(),
        email: email.trim() || undefined,
      });
    }
  };

  const isLoading = createMutation.isPending || updateMutation.isPending;

  return (
    <>
      <DialogTitle>{user ? 'Edit Admin User' : 'Create Admin User'}</DialogTitle>
      <DialogContent sx={{ pt: 2, display: 'flex', flexDirection: 'column', gap: 2 }}>
        {error && <Alert severity="error">{error}</Alert>}

        <TextField
          label="Username"
          fullWidth
          value={username}
          onChange={(e) => setUsername(e.target.value)}
          disabled={isLoading}
          required
        />

        {!user && (
          <TextField
            label="Password"
            fullWidth
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            disabled={isLoading}
            required
            helperText="Password is required for new admin users"
          />
        )}

        <TextField
          label="Email"
          fullWidth
          type="email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          disabled={isLoading}
        />

        {user && (
          <FormControlLabel
            control={
              <Checkbox
                checked={isActive}
                onChange={(e) => setIsActive(e.target.checked)}
                disabled={isLoading}
              />
            }
            label="Active"
          />
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={onCancel} disabled={isLoading}>
          Cancel
        </Button>
        <Button onClick={handleSubmit} variant="contained" disabled={isLoading}>
          {isLoading && <CircularProgress size={20} sx={{ mr: 1 }} />}
          {user ? 'Update' : 'Create'}
        </Button>
      </DialogActions>
    </>
  );
}
