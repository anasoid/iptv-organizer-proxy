import { useEffect, useState } from 'react';
import {
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
  Button,
  Box,
  CircularProgress,
  Alert,
  FormControlLabel,
  Checkbox,
  MenuItem,
  Typography,
} from '@mui/material';
import { useForm, Controller } from 'react-hook-form';
import { useMutation, useQuery } from '@tanstack/react-query';
import { ContentCopy as CopyIcon } from '@mui/icons-material';
import type { Client } from '../services/clientsApi';
import clientsApi from '../services/clientsApi';
import sourcesApi from '../services/sourcesApi';
import filtersApi from '../services/filtersApi';

interface ClientFormProps {
  client?: Client | null;
  onSuccess: () => void;
  onCancel: () => void;
}

export default function ClientForm({ client, onSuccess, onCancel }: ClientFormProps) {
  const [showPassword, setShowPassword] = useState(false);
  const [credentials, setCredentials] = useState<{ username: string; password: string } | null>(
    null
  );

  const {
    register,
    handleSubmit,
    formState: { errors },
    reset,
    watch,
    setValue,
    control,
  } = useForm<Client>({
    defaultValues: client || {
      username: '',
      password: '',
      name: '',
      source_id: undefined,
      filter_id: null,
      email: '',
      expiry_date: undefined,
      is_active: 1,
      hide_adult_content: 0,
      use_redirect: null,
      use_redirect_xmltv: null,
      enableproxy: null,
      disablestreamproxy: null,
      stream_follow_location: null,
      notes: '',
    },
  });

  const sourceId = watch('source_id');
  const filterId = watch('filter_id');

  // Fetch sources for dropdown
  const { data: sourcesData } = useQuery({
    queryKey: ['sources'],
    queryFn: () => sourcesApi.getSources(1, 100),
  });

  // Fetch filters for dropdown
  const { data: filtersData } = useQuery({
    queryKey: ['filters'],
    queryFn: () => filtersApi.getFilters(1, 100),
  });

  // Create mutation
  const createMutation = useMutation({
    mutationFn: (data: Omit<Client, 'id' | 'created_at' | 'updated_at'>) =>
      clientsApi.createClient(data),
    onSuccess,
  });

  // Update mutation
  const updateMutation = useMutation({
    mutationFn: (data: { id: number; client: Partial<Client> }) =>
      clientsApi.updateClient(data.id, data.client),
    onSuccess,
  });

  useEffect(() => {
    if (client) {
      reset(client);
    }
  }, [client, reset]);

  const onSubmit = (data: Client) => {
    if (client?.id) {
      updateMutation.mutate({
        id: client.id,
        client: data,
      });
    } else {
      createMutation.mutate(data);
    }
  };

  const generateCredentials = () => {
    const newUsername = `client_${Date.now()}`;
    const newPassword = Math.random().toString(36).substring(2, 15) +
      Math.random().toString(36).substring(2, 15);

    setCredentials({ username: newUsername, password: newPassword });
    setValue('username', newUsername);
    setValue('password', newPassword);
  };

  const copyToClipboard = () => {
    if (sourceId && credentials) {
      const connectionUrl = `http://proxy-url/player_api.php?username=${credentials.username}&password=${credentials.password}&source=${sourceId}`;
      navigator.clipboard.writeText(connectionUrl).then(() => {
        alert('Credentials copied to clipboard!');
      });
    }
  };

  const isLoading = createMutation.isPending || updateMutation.isPending;
  const error = createMutation.error || updateMutation.error;

  return (
    <>
      <DialogTitle>{client ? 'Edit Client' : 'Add Client'}</DialogTitle>
      <DialogContent sx={{ pt: 2, display: 'flex', flexDirection: 'column', gap: 2 }}>
        {error && (
          <Alert severity="error">
            {error instanceof Error ? error.message : 'An error occurred'}
          </Alert>
        )}

        <TextField
          select
          label="Source"
          fullWidth
          value={sourceId || ''}
          {...register('source_id', { required: 'Source is required' })}
          error={!!errors.source_id}
          helperText={errors.source_id?.message}
        >
          <MenuItem value="">Select a source</MenuItem>
          {sourcesData?.data?.map(
            (source: { id: number; name: string }) => (
              <MenuItem key={source.id} value={source.id}>
                {source.name}
              </MenuItem>
            )
          )}
        </TextField>

        <TextField
          select
          label="Filter (Optional)"
          fullWidth
          value={filterId || ''}
          {...register('filter_id')}
        >
          <MenuItem value="">None</MenuItem>
          {Array.isArray(filtersData?.data) &&
            filtersData.data.map(
              (filter: { id: number; name: string }) => (
                <MenuItem key={filter.id} value={filter.id}>
                  {filter.name}
                </MenuItem>
              )
            )}
        </TextField>

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
          type={showPassword ? 'text' : 'password'}
          {...register('password', { required: 'Password is required' })}
          error={!!errors.password}
          helperText={errors.password?.message}
        />

        <FormControlLabel
          control={<Checkbox checked={showPassword} onChange={(e) => setShowPassword(e.target.checked)} />}
          label="Show password"
        />

        <Button onClick={generateCredentials} variant="outlined" fullWidth>
          Generate Random Credentials
        </Button>

        <TextField
          label="Email"
          fullWidth
          type="email"
          {...register('email', {
            pattern: {
              value: /^[^\s@]+@[^\s@]+\.[^\s@]+$/,
              message: 'Invalid email format',
            },
          })}
          error={!!errors.email}
          helperText={errors.email?.message}
        />

        <TextField
          label="Display Name (Optional)"
          fullWidth
          {...register('name')}
        />

        <TextField
          label="Expiry Date (Optional)"
          fullWidth
          type="date"
          slotProps={{ input: { max: '2099-12-31' } }}
          {...register('expiry_date')}
        />

        <FormControlLabel
          control={
            <Checkbox
              {...register('is_active')}
              defaultChecked={!client || client.is_active === 1}
            />
          }
          label="Active"
        />

        <FormControlLabel
          control={
            <Checkbox
              {...register('hide_adult_content')}
              defaultChecked={client?.hide_adult_content === 1}
            />
          }
          label="Hide Adult Content"
        />

        <TextField
          label="Notes (Optional)"
          fullWidth
          multiline
          rows={3}
          {...register('notes')}
        />

        <Box sx={{ borderTop: 1, borderColor: 'divider', pt: 2, mt: 2 }}>
          <Typography variant="subtitle2" sx={{ mb: 2, fontWeight: 600 }}>
            Stream & XMLTV Redirect Settings (Optional - leave unchecked to inherit from source/env)
          </Typography>

          <Controller
            name="use_redirect"
            control={control}
            render={({ field }) => (
              <FormControlLabel
                control={
                  <Checkbox
                    checked={Boolean(field.value)}
                    onChange={(e) => field.onChange(e.target.checked ? 1 : null)}
                    indeterminate={field.value === null}
                  />
                }
                label="Enable Stream Direct Redirect (302)"
              />
            )}
          />
          <Typography variant="caption" sx={{ display: 'block', color: 'text.secondary', ml: 4, mb: 2 }}>
            When enabled, stream requests return a direct 302 redirect instead of proxying through /proxy endpoint
          </Typography>

          <Controller
            name="use_redirect_xmltv"
            control={control}
            render={({ field }) => (
              <FormControlLabel
                control={
                  <Checkbox
                    checked={Boolean(field.value)}
                    onChange={(e) => field.onChange(e.target.checked ? 1 : null)}
                    indeterminate={field.value === null}
                  />
                }
                label="Enable XMLTV Direct Redirect (302)"
              />
            )}
          />
          <Typography variant="caption" sx={{ display: 'block', color: 'text.secondary', ml: 4, mb: 2 }}>
            When enabled, XMLTV EPG requests return a direct 302 redirect instead of streaming content
          </Typography>

          <Box sx={{ borderTop: 1, borderColor: 'divider', pt: 2, mt: 2 }}>
            <Typography variant="caption" sx={{ fontWeight: 600 }}>
              Proxy Settings (Optional - leave unchecked to inherit from source)
            </Typography>

            <Controller
              name="enableproxy"
              control={control}
              render={({ field }) => (
                <FormControlLabel
                  control={
                    <Checkbox
                      checked={Boolean(field.value)}
                      onChange={(e) => field.onChange(e.target.checked ? 1 : null)}
                      indeterminate={field.value === null}
                    />
                  }
                  label="Enable HTTP Proxy"
                />
              )}
            />

            <Controller
              name="disablestreamproxy"
              control={control}
              render={({ field }) => (
                <FormControlLabel
                  control={
                    <Checkbox
                      checked={Boolean(field.value)}
                      onChange={(e) => field.onChange(e.target.checked ? 1 : null)}
                      indeterminate={field.value === null}
                    />
                  }
                  label="Disable Stream Proxy"
                />
              )}
            />

            <Controller
              name="stream_follow_location"
              control={control}
              render={({ field }) => (
                <FormControlLabel
                  control={
                    <Checkbox
                      checked={Boolean(field.value)}
                      onChange={(e) => field.onChange(e.target.checked ? 1 : null)}
                      indeterminate={field.value === null}
                    />
                  }
                  label="Follow HTTP Redirects"
                />
              )}
            />
          </Box>
        </Box>

        {credentials && sourceId && (
          <Box sx={{ p: 2, bgcolor: 'info.light', borderRadius: 1 }}>
            <Alert severity="info">
              Connection URL:
              <br />
              <code style={{ wordBreak: 'break-all' }}>
                http://proxy-url/player_api.php?username={credentials.username}&password=
                {credentials.password}
              </code>
            </Alert>
            <Button
              startIcon={<CopyIcon />}
              onClick={copyToClipboard}
              variant="outlined"
              size="small"
              sx={{ mt: 1 }}
            >
              Copy URL
            </Button>
          </Box>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={onCancel}>Cancel</Button>
        <Button onClick={handleSubmit(onSubmit)} variant="contained" disabled={isLoading}>
          {isLoading && <CircularProgress size={20} sx={{ mr: 1 }} />}
          {client ? 'Update' : 'Create'}
        </Button>
      </DialogActions>
    </>
  );
}
