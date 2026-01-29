import { useEffect } from 'react';
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

  const {
    register,
    handleSubmit,
    formState: { errors },
    reset,
    watch,
    control,
  } = useForm<Client>({
    defaultValues: client || {
      username: '',
      password: '',
      name: '',
      sourceId: undefined,
      filterId: null,
      email: '',
      expiryDate: undefined,
      isActive: true,
      hideAdultContent: false,
      useRedirect: null,
      useRedirectXmltv: null,
      enableProxy: null,
      disableStreamProxy: null,
      streamFollowLocation: null,
      notes: '',
    },
  });

  const sourceId = watch('sourceId');
  const filterId = watch('filterId');

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


  const isLoading = createMutation.isPending || updateMutation.isPending;
  const error = createMutation.error || updateMutation.error;

  return (
    <>
      <DialogTitle>{client ? 'Edit Client' : 'Add Client'}</DialogTitle>
      <DialogContent sx={{ pt: 2, display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 2 }}>
        {error && (
          <Alert severity="error" sx={{ gridColumn: '1 / -1' }}>
            {error instanceof Error ? error.message : 'An error occurred'}
          </Alert>
        )}

        <TextField
          select
          label="Source"
          fullWidth
          value={sourceId || ''}
          {...register('sourceId', { required: 'Source is required' })}
          error={!!errors.sourceId}
          helperText={errors.sourceId?.message}
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
          {...register('filterId')}
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
          type="text"
          {...register('password', { required: 'Password is required' })}
          error={!!errors.password}
          helperText={errors.password?.message}
        />

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
          {...register('expiryDate')}
        />

        <Box sx={{ gridColumn: '1 / -1' }}>
          <FormControlLabel
            control={
              <Checkbox
                {...register('isActive')}
                defaultChecked={!client || client.isActive === true}
              />
            }
            label="Active"
          />
        </Box>

        <Box sx={{ gridColumn: '1 / -1' }}>
          <FormControlLabel
            control={
              <Checkbox
                {...register('hideAdultContent')}
                defaultChecked={client?.hideAdultContent === true}
              />
            }
            label="Hide Adult Content"
          />
        </Box>


        <TextField
          label="Notes (Optional)"
          fullWidth
          multiline
          rows={3}
          {...register('notes')}
          sx={{ gridColumn: '1 / -1' }}
        />

        <Box sx={{ borderTop: 1, borderColor: 'divider', pt: 2, mt: 2, gridColumn: '1 / -1' }}>
          <Typography variant="subtitle2" sx={{ mb: 2, fontWeight: 600 }}>
            Stream & XMLTV Redirect Settings (Optional - leave unchecked to inherit from source/env)
          </Typography>

          <Box sx={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 2 }}>
            <Box>
              <Controller
                name="useRedirect"
                control={control}
                render={({ field }) => (
                  <FormControlLabel
                    control={
                      <Checkbox
                        checked={Boolean(field.value)}
                        onChange={(e) => field.onChange(e.target.checked ? true : false)}
                        indeterminate={field.value === null}
                      />
                    }
                    label="Enable Stream Direct Redirect (302)"
                  />
                )}
              />
              <Typography variant="caption" sx={{ display: 'block', color: 'text.secondary', ml: 4 }}>
                When enabled, stream requests return a direct 302 redirect instead of proxying through /proxy endpoint
              </Typography>
            </Box>

            <Box>
              <Controller
                name="useRedirectXmltv"
                control={control}
                render={({ field }) => (
                  <FormControlLabel
                    control={
                      <Checkbox
                        checked={Boolean(field.value)}
                        onChange={(e) => field.onChange(e.target.checked ? true : false)}
                        indeterminate={field.value === null}
                      />
                    }
                    label="Enable XMLTV Direct Redirect (302)"
                  />
                )}
              />
              <Typography variant="caption" sx={{ display: 'block', color: 'text.secondary', ml: 4 }}>
                When enabled, XMLTV EPG requests return a direct 302 redirect instead of streaming content
              </Typography>
            </Box>
          </Box>

          <Box sx={{ borderTop: 1, borderColor: 'divider', pt: 2, mt: 2 }}>
            <Typography variant="caption" sx={{ fontWeight: 600 }}>
              Proxy Settings (Optional - leave unchecked to inherit from source)
            </Typography>

            <Box sx={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 2, mt: 1 }}>
              <Box>
                <Controller
                  name="enableProxy"
                  control={control}
                  render={({ field }) => (
                    <FormControlLabel
                      control={
                        <Checkbox
                          checked={Boolean(field.value)}
                          onChange={(e) => field.onChange(e.target.checked ? true : false)}
                          indeterminate={field.value === null}
                        />
                      }
                      label="Enable HTTP Proxy"
                    />
                  )}
                />
              </Box>

              <Box>
                <Controller
                  name="disableStreamProxy"
                  control={control}
                  render={({ field }) => (
                    <FormControlLabel
                      control={
                        <Checkbox
                          checked={Boolean(field.value)}
                          onChange={(e) => field.onChange(e.target.checked ? true : false)}
                          indeterminate={field.value === null}
                        />
                      }
                      label="Disable Stream Proxy"
                    />
                  )}
                />
              </Box>

              <Box sx={{ gridColumn: '1 / -1' }}>
                <Controller
                  name="streamFollowLocation"
                  control={control}
                  render={({ field }) => (
                    <FormControlLabel
                      control={
                        <Checkbox
                          checked={Boolean(field.value)}
                          onChange={(e) => field.onChange(e.target.checked ? true : false)}
                          indeterminate={field.value === null}
                        />
                      }
                      label="Follow HTTP Redirects"
                    />
                  )}
                />
              </Box>
            </Box>
          </Box>
        </Box>

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
