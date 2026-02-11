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
  FormControl,
  InputLabel,
  Select,
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
    getValues,
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
      enableProxy: null,
      enableTunnel: null,
      connectXtreamApi: null,
      connectXtreamStream: null,
      connectXmltv: null,
      notes: '',
    },
  });

  const sourceId = watch('sourceId');
  const filterId = watch('filterId');
  const enableProxy = watch('enableProxy');
  const enableTunnel = watch('enableTunnel');

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
      // Reset form with client data, preserving null values for three-state checkboxes
      reset({
        ...client,
        enableProxy: client.enableProxy ?? null,
        enableTunnel: client.enableTunnel ?? null,
      });
    }
  }, [client, reset]);

  const onSubmit = (data: Client) => {
    // Ensure null values are preserved in the submission
    // enableProxy and enableTunnel can be null (for inheritance) or true/false
    const clientData = {
      ...data,
      // Explicitly preserve null values
      enableProxy: data.enableProxy === undefined ? null : data.enableProxy,
      enableTunnel: data.enableTunnel === undefined ? null : data.enableTunnel,
    };

    console.log('Submitting client data:', clientData);

    if (client?.id) {
      updateMutation.mutate({
        id: client.id,
        client: clientData,
      });
    } else {
      createMutation.mutate(clientData);
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

        <FormControlLabel
          control={
            <Checkbox
              {...register('isActive')}
              defaultChecked={!client || client.isActive === true}
            />
          }
          label="Active"
        />

        <FormControlLabel
          control={
            <Checkbox
              {...register('hideAdultContent')}
              defaultChecked={client?.hideAdultContent === true}
            />
          }
          label="Hide Adult Content"
        />

        <Box sx={{ borderTop: 1, borderColor: 'divider', pt: 2, mt: 2, gridColumn: '1 / -1' }}>
          <Typography variant="subtitle2" sx={{ mb: 2, fontWeight: 600 }}>
            Proxy Settings (Optional)
          </Typography>
          <Typography variant="caption" sx={{ display: 'block', color: 'text.secondary', mb: 2 }}>
            Indeterminate (unchecked) = inherit from source, Checked = enable, Unchecked = disable
          </Typography>

          <Box sx={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 2 }}>
            <Box>
              <Controller
                name="enableProxy"
                control={control}
                shouldUnregister={false}
                render={({ field }) => (
                  <FormControlLabel
                    control={
                      <Checkbox
                        checked={field.value === true}
                        indeterminate={field.value === null}
                        onChange={(e) => {
                          const currentValue = field.value;
                          // Three-state cycle: null -> true -> false -> null
                          let newValue: boolean | null;
                          if (currentValue === null) {
                            newValue = true;
                          } else if (currentValue === true) {
                            newValue = false;
                          } else {
                            newValue = null;
                          }
                          field.onChange(newValue);
                        }}
                        inputProps={{ 'data-testid': field.name }}
                      />
                    }
                    label="Enable HTTP Proxy"
                  />
                )}
              />
              <Typography variant="caption" sx={{ display: 'block', color: 'text.secondary', ml: 4 }}>
                Use HTTP proxy for upstream requests
              </Typography>
            </Box>

            <Box>
              <Controller
                name="enableTunnel"
                control={control}
                shouldUnregister={false}
                render={({ field }) => (
                  <FormControlLabel
                    control={
                      <Checkbox
                        checked={field.value === true}
                        indeterminate={field.value === null}
                        onChange={(e) => {
                          const currentValue = field.value;
                          // Three-state cycle: null -> true -> false -> null
                          let newValue: boolean | null;
                          if (currentValue === null) {
                            newValue = true;
                          } else if (currentValue === true) {
                            newValue = false;
                          } else {
                            newValue = null;
                          }
                          field.onChange(newValue);
                        }}
                        inputProps={{ 'data-testid': field.name }}
                      />
                    }
                    label="Enable Tunnel"
                  />
                )}
              />
              <Typography variant="caption" sx={{ display: 'block', color: 'text.secondary', ml: 4 }}>
                Use reverse proxy tunnel
              </Typography>
            </Box>
          </Box>
        </Box>

        <Box sx={{ borderTop: 1, borderColor: 'divider', pt: 2, mt: 2, gridColumn: '1 / -1' }}>
          <Typography variant="subtitle2" sx={{ mb: 2, fontWeight: 600 }}>
            Connection Mode Settings (Optional - leave empty to inherit from source)
          </Typography>

          <Box sx={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 2 }}>
            <Controller
              name="connectXtreamApi"
              control={control}
              render={({ field }) => (
                <FormControl fullWidth>
                  <InputLabel>Xtream API Mode</InputLabel>
                  <Select
                    value={field.value ?? ''}
                    onChange={(e) => {
                      const val = e.target.value;
                      field.onChange(val === '' ? null : val);
                    }}
                    label="Xtream API Mode"
                  >
                    <MenuItem value="">Inherit from Source</MenuItem>
                    <MenuItem value="INHERITED">Force Inherit</MenuItem>
                    <MenuItem value="DEFAULT">Default</MenuItem>
                    <MenuItem value="TUNNEL">Tunnel</MenuItem>
                    <MenuItem value="PROXY">Proxy</MenuItem>
                  </Select>
                </FormControl>
              )}
            />

            <Controller
              name="connectXtreamStream"
              control={control}
              render={({ field }) => (
                <FormControl fullWidth>
                  <InputLabel>Xtream Stream Mode</InputLabel>
                  <Select
                    value={field.value ?? ''}
                    onChange={(e) => {
                      const val = e.target.value;
                      field.onChange(val === '' ? null : val);
                    }}
                    label="Xtream Stream Mode"
                  >
                    <MenuItem value="">Inherit from Source</MenuItem>
                    <MenuItem value="INHERITED">Force Inherit</MenuItem>
                    <MenuItem value="DIRECT">Direct</MenuItem>
                    <MenuItem value="TUNNEL">Tunnel</MenuItem>
                    <MenuItem value="PROXY">Proxy</MenuItem>
                    <MenuItem value="REDIRECT">Redirect</MenuItem>
                    <MenuItem value="DEFAULT">Default</MenuItem>
                  </Select>
                </FormControl>
              )}
            />

            <Controller
              name="connectXmltv"
              control={control}
              render={({ field }) => (
                <FormControl fullWidth>
                  <InputLabel>XMLTV Mode</InputLabel>
                  <Select
                    value={field.value ?? ''}
                    onChange={(e) => {
                      const val = e.target.value;
                      field.onChange(val === '' ? null : val);
                    }}
                    label="XMLTV Mode"
                  >
                    <MenuItem value="">Inherit from Source</MenuItem>
                    <MenuItem value="INHERITED">Force Inherit</MenuItem>
                    <MenuItem value="REDIRECT">Redirect</MenuItem>
                    <MenuItem value="TUNNEL">Tunnel</MenuItem>
                    <MenuItem value="PROXY">Proxy</MenuItem>
                    <MenuItem value="DEFAULT">Default</MenuItem>
                  </Select>
                </FormControl>
              )}
            />
          </Box>
        </Box>

        <TextField
          label="Notes (Optional)"
          fullWidth
          multiline
          rows={3}
          {...register('notes')}
          sx={{ gridColumn: '1 / -1' }}
        />


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
