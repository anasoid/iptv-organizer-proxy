import { useEffect } from 'react';
import {
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
  Button,
  CircularProgress,
  Alert,
} from '@mui/material';
import { useForm } from 'react-hook-form';
import { useMutation } from '@tanstack/react-query';
import type { Source } from '../services/sourcesApi';
import sourcesApi from '../services/sourcesApi';

interface SourceFormProps {
  source?: Source | null;
  onSuccess: () => void;
  onCancel: () => void;
}

export default function SourceForm({ source, onSuccess, onCancel }: SourceFormProps) {
  const {
    register,
    handleSubmit,
    formState: { errors },
    reset,
    watch,
  } = useForm<Source>({
    defaultValues: source || {
      name: '',
      url: '',
      username: '',
      password: '',
      sync_interval: 1,
      is_active: 1,
      sync_status: 'idle',
    },
  });

  // eslint-disable-next-line react-hooks/incompatible-library
  const urlValue = watch('url');

  // Create mutation
  const createMutation = useMutation({
    mutationFn: (data: Omit<Source, 'id' | 'created_at' | 'updated_at'>) =>
      sourcesApi.createSource(data),
    onSuccess,
  });

  // Update mutation
  const updateMutation = useMutation({
    mutationFn: (data: { id: number; source: Partial<Source> }) =>
      sourcesApi.updateSource(data.id, data.source),
    onSuccess,
  });

  // Test connection mutation
  const testMutation = useMutation({
    mutationFn: (id: number) => sourcesApi.testConnection(id),
  });

  useEffect(() => {
    if (source) {
      reset(source);
    }
  }, [source, reset]);

  const onSubmit = (data: Source) => {
    if (source?.id) {
      updateMutation.mutate({
        id: source.id,
        source: data,
      });
    } else {
      createMutation.mutate(data);
    }
  };

  const handleTestConnection = async () => {
    if (source?.id) {
      testMutation.mutate(source.id);
    }
  };

  const isLoading = createMutation.isPending || updateMutation.isPending;
  const error = createMutation.error || updateMutation.error;

  return (
    <>
      <DialogTitle>{source ? 'Edit Source' : 'Add Source'}</DialogTitle>
      <DialogContent sx={{ pt: 2, display: 'flex', flexDirection: 'column', gap: 2 }}>
        {error && (
          <Alert severity="error">
            {error instanceof Error ? error.message : 'An error occurred'}
          </Alert>
        )}
        {testMutation.data && (
          <Alert severity="success">
            {testMutation.data.connected ? 'Connection successful!' : 'Connection failed'}
          </Alert>
        )}

        <TextField
          label="Name"
          fullWidth
          {...register('name', { required: 'Name is required' })}
          error={!!errors.name}
          helperText={errors.name?.message}
        />

        <TextField
          label="URL"
          fullWidth
          type="url"
          {...register('url', {
            required: 'URL is required',
            pattern: {
              value: /^https?:\/\/.+/,
              message: 'Please enter a valid URL',
            },
          })}
          error={!!errors.url}
          helperText={errors.url?.message}
        />

        <TextField
          label="Username"
          fullWidth
          {...register('username', { required: 'Username is required' })}
          error={!!errors.username}
          helperText={errors.username?.message}
        />

        <TextField
          label="Password"
          fullWidth
          type="password"
          {...register('password', { required: 'Password is required' })}
          error={!!errors.password}
          helperText={errors.password?.message}
        />

        <TextField
          label="Sync Interval (days)"
          fullWidth
          type="number"
          inputProps={{ min: 1 }}
          {...register('sync_interval', {
            required: 'Sync interval is required',
            min: { value: 1, message: 'Minimum is 1 day' },
          })}
          error={!!errors.sync_interval}
          helperText={errors.sync_interval?.message}
        />
      </DialogContent>
      <DialogActions>
        <Button onClick={onCancel}>Cancel</Button>
        {source?.id && urlValue && (
          <Button
            onClick={handleTestConnection}
            disabled={testMutation.isPending}
            variant="outlined"
          >
            {testMutation.isPending ? 'Testing...' : 'Test Connection'}
          </Button>
        )}
        <Button
          onClick={handleSubmit(onSubmit)}
          variant="contained"
          disabled={isLoading}
        >
          {isLoading && <CircularProgress size={20} sx={{ mr: 1 }} />}
          {source ? 'Update' : 'Create'}
        </Button>
      </DialogActions>
    </>
  );
}
