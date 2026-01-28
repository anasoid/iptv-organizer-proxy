import { useState } from 'react';
import {
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
  Button,
  Alert,
  Select,
  MenuItem,
  FormControl,
  InputLabel,
  CircularProgress,
} from '@mui/material';
import { useMutation } from '@tanstack/react-query';
import type { Proxy } from '../services/proxiesApi';
import proxiesApi from '../services/proxiesApi';

interface ProxyFormProps {
  proxy: Proxy | null;
  onSuccess: () => void;
  onCancel: () => void;
}

export default function ProxyForm({ proxy, onSuccess, onCancel }: ProxyFormProps) {
  const [name, setName] = useState(proxy?.name || '');
  const [description, setDescription] = useState(proxy?.description || '');
  const [proxyUrl, setProxyUrl] = useState(proxy?.proxy_url || '');
  const [proxyHost, setProxyHost] = useState(proxy?.proxy_host || '');
  const [proxyPort, setProxyPort] = useState(proxy?.proxy_port?.toString() || '');
  const [proxyType, setProxyType] = useState<'HTTP' | 'HTTPS' | 'SOCKS5' | ''>(
    proxy?.proxy_type || ''
  );
  const [proxyUsername, setProxyUsername] = useState(proxy?.proxy_username || '');
  const [proxyPassword, setProxyPassword] = useState(proxy?.proxy_password || '');
  const [timeout, setTimeout] = useState(proxy?.timeout?.toString() || '');
  const [maxRetries, setMaxRetries] = useState(proxy?.max_retries?.toString() || '');
  const [error, setError] = useState<string | null>(null);

  const createMutation = useMutation({
    mutationFn: (data: Parameters<typeof proxiesApi.createProxy>[0]) =>
      proxiesApi.createProxy(data),
    onSuccess: () => {
      onSuccess();
    },
    onError: (err) => {
      setError(err instanceof Error ? err.message : 'Failed to create proxy');
    },
  });

  const updateMutation = useMutation({
    mutationFn: (data: Parameters<typeof proxiesApi.updateProxy>[1]) =>
      proxiesApi.updateProxy(proxy!.id!, data),
    onSuccess: () => {
      onSuccess();
    },
    onError: (err) => {
      setError(err instanceof Error ? err.message : 'Failed to update proxy');
    },
  });

  const handleSubmit = async () => {
    // Validation
    if (!name.trim()) {
      setError('Proxy name is required');
      return;
    }

    // Validate port if provided
    if (proxyPort && (isNaN(Number(proxyPort)) || Number(proxyPort) < 1 || Number(proxyPort) > 65535)) {
      setError('Port must be a number between 1 and 65535');
      return;
    }

    // Validate timeout if provided
    if (timeout && (isNaN(Number(timeout)) || Number(timeout) < 0)) {
      setError('Timeout must be a positive number');
      return;
    }

    // Validate maxRetries if provided
    if (maxRetries && (isNaN(Number(maxRetries)) || Number(maxRetries) < 0)) {
      setError('Max retries must be a positive number');
      return;
    }

    // Validate that either proxyUrl or proxyHost is provided
    if (!proxyUrl.trim() && !proxyHost.trim()) {
      setError('Either Proxy URL or Proxy Host must be provided');
      return;
    }

    setError(null);

    const proxyData = {
      name: name.trim(),
      description: description.trim() || null,
      proxyUrl: proxyUrl.trim() || null,
      proxyHost: proxyHost.trim() || null,
      proxyPort: proxyPort ? Number(proxyPort) : null,
      proxyType: proxyType || null,
      proxyUsername: proxyUsername.trim() || null,
      proxyPassword: proxyPassword.trim() || null,
      timeout: timeout ? Number(timeout) : null,
      maxRetries: maxRetries ? Number(maxRetries) : null,
    };

    if (proxy) {
      // Update mode
      updateMutation.mutate(proxyData);
    } else {
      // Create mode
      createMutation.mutate(proxyData);
    }
  };

  const isLoading = createMutation.isPending || updateMutation.isPending;

  return (
    <>
      <DialogTitle>{proxy ? 'Edit Proxy' : 'Add Proxy'}</DialogTitle>
      <DialogContent sx={{ minWidth: 500 }}>
        {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}

        <TextField
          fullWidth
          label="Proxy Name"
          value={name}
          onChange={(e) => setName(e.target.value)}
          margin="normal"
          disabled={isLoading}
        />

        <TextField
          fullWidth
          label="Description"
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          margin="normal"
          multiline
          rows={2}
          disabled={isLoading}
        />

        <TextField
          fullWidth
          label="Proxy URL"
          placeholder="http://user:pass@proxy.example.com:8080"
          value={proxyUrl}
          onChange={(e) => setProxyUrl(e.target.value)}
          margin="normal"
          disabled={isLoading}
          helperText="Optional: Full proxy URL (alternative to separate host/port)"
        />

        <TextField
          fullWidth
          label="Proxy Host"
          value={proxyHost}
          onChange={(e) => setProxyHost(e.target.value)}
          margin="normal"
          disabled={isLoading}
          helperText="Proxy server hostname or IP"
        />

        <TextField
          fullWidth
          label="Proxy Port"
          type="number"
          value={proxyPort}
          onChange={(e) => setProxyPort(e.target.value)}
          margin="normal"
          disabled={isLoading}
          inputProps={{ min: 1, max: 65535 }}
        />

        <FormControl fullWidth margin="normal" disabled={isLoading}>
          <InputLabel>Proxy Type</InputLabel>
          <Select
            value={proxyType}
            onChange={(e) => setProxyType(e.target.value as 'HTTP' | 'HTTPS' | 'SOCKS5' | '')}
            label="Proxy Type"
          >
            <MenuItem value="">None</MenuItem>
            <MenuItem value="HTTP">HTTP</MenuItem>
            <MenuItem value="HTTPS">HTTPS</MenuItem>
            <MenuItem value="SOCKS5">SOCKS5</MenuItem>
          </Select>
        </FormControl>

        <TextField
          fullWidth
          label="Username"
          value={proxyUsername}
          onChange={(e) => setProxyUsername(e.target.value)}
          margin="normal"
          disabled={isLoading}
          helperText="Optional: Authentication username"
        />

        <TextField
          fullWidth
          label="Password"
          type="password"
          value={proxyPassword}
          onChange={(e) => setProxyPassword(e.target.value)}
          margin="normal"
          disabled={isLoading}
          helperText="Optional: Authentication password"
        />

        <TextField
          fullWidth
          label="Timeout (ms)"
          type="number"
          value={timeout}
          onChange={(e) => setTimeout(e.target.value)}
          margin="normal"
          disabled={isLoading}
          inputProps={{ min: 0 }}
          helperText="Request timeout in milliseconds"
        />

        <TextField
          fullWidth
          label="Max Retries"
          type="number"
          value={maxRetries}
          onChange={(e) => setMaxRetries(e.target.value)}
          margin="normal"
          disabled={isLoading}
          inputProps={{ min: 0 }}
          helperText="Maximum number of retry attempts"
        />
      </DialogContent>
      <DialogActions>
        <Button onClick={onCancel} disabled={isLoading}>
          Cancel
        </Button>
        <Button onClick={handleSubmit} variant="contained" disabled={isLoading}>
          {isLoading ? <CircularProgress size={24} /> : proxy ? 'Update' : 'Create'}
        </Button>
      </DialogActions>
    </>
  );
}
