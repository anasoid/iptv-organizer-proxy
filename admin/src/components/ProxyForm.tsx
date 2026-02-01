import { useState, useEffect } from 'react';
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

// Helper function to extract proxy type from URL
function detectProxyTypeFromUrl(url: string): 'HTTP' | 'HTTPS' | 'SOCKS5' | '' {
  if (!url) return '';
  const urlLower = url.toLowerCase();
  if (urlLower.startsWith('socks5://')) return 'SOCKS5';
  if (urlLower.startsWith('https://')) return 'HTTPS';
  if (urlLower.startsWith('http://')) return 'HTTP';
  return '';
}

// Helper function to get property value (handles both camelCase and snake_case)
function getProperty(obj: any, camelCase: string, snakeCase: string): any {
  return obj?.[camelCase] ?? obj?.[snakeCase] ?? undefined;
}

export default function ProxyForm({ proxy, onSuccess, onCancel }: ProxyFormProps) {
  // Initialize with empty state - will be populated by useEffect
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [proxyUrl, setProxyUrl] = useState('');
  const [proxyHost, setProxyHost] = useState('');
  const [proxyPort, setProxyPort] = useState('');
  const [proxyType, setProxyType] = useState<'HTTP' | 'HTTPS' | 'SOCKS5' | ''>('');
  const [proxyUsername, setProxyUsername] = useState('');
  const [proxyPassword, setProxyPassword] = useState('');
  const [timeout, setTimeout] = useState('');
  const [maxRetries, setMaxRetries] = useState('');
  const [error, setError] = useState<string | null>(null);

  // Update form when proxy prop changes (for edit mode)
  useEffect(() => {
    if (proxy) {
      const nameVal = getProperty(proxy, 'name', 'name');
      const descVal = getProperty(proxy, 'description', 'description');
      const urlVal = getProperty(proxy, 'proxyUrl', 'proxy_url');
      const hostVal = getProperty(proxy, 'proxyHost', 'proxy_host');
      const portVal = getProperty(proxy, 'proxyPort', 'proxy_port');
      const typeVal = getProperty(proxy, 'proxyType', 'proxy_type');
      const usernameVal = getProperty(proxy, 'proxyUsername', 'proxy_username');
      const passwordVal = getProperty(proxy, 'proxyPassword', 'proxy_password');
      const timeoutVal = getProperty(proxy, 'timeout', 'timeout');
      const retriesVal = getProperty(proxy, 'maxRetries', 'max_retries');

      setName(nameVal || '');
      setDescription(descVal || '');
      setProxyUrl(urlVal || '');
      setProxyHost(hostVal || '');
      setProxyPort(portVal ? String(portVal) : '');
      setProxyType(detectProxyTypeFromUrl(urlVal || '') || typeVal || '');
      setProxyUsername(usernameVal || '');
      setProxyPassword(passwordVal || '');
      setTimeout(timeoutVal ? String(timeoutVal) : '');
      setMaxRetries(retriesVal ? String(retriesVal) : '');
    } else {
      // Clear form for create mode
      setName('');
      setDescription('');
      setProxyUrl('');
      setProxyHost('');
      setProxyPort('');
      setProxyType('');
      setProxyUsername('');
      setProxyPassword('');
      setTimeout('');
      setMaxRetries('');
    }
  }, [proxy]);

  // Auto-detect proxy type when proxyUrl changes
  useEffect(() => {
    const detectedType = detectProxyTypeFromUrl(proxyUrl);
    if (detectedType) {
      setProxyType(detectedType);
    }
  }, [proxyUrl]);

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

    const hasProxyUrl = proxyUrl.trim();
    const hasProxyHost = proxyHost.trim();

    // Validate that either proxyUrl or proxyHost is provided, but not both
    if (!hasProxyUrl && !hasProxyHost) {
      setError('Either Proxy URL or Proxy Host must be provided');
      return;
    }

    if (hasProxyUrl && hasProxyHost) {
      setError('Cannot use both Proxy URL and Proxy Host/Port. Choose one configuration method.');
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

    setError(null);

    // Build proxy data
    // Note: Server checks "if != null" so we must send actual values (including empty string) not null
    const proxyData = {
      name: name.trim(),
      description: description.trim() || null,
      proxyUrl: proxyUrl.trim(),  // Send empty string to clear, null to skip update
      proxyHost: proxyHost.trim(),  // Send empty string to clear, null to skip update
      proxyPort: proxyPort ? Number(proxyPort) : null,
      proxyType: proxyType || null,
      proxyUsername: proxyUsername.trim(),  // Send empty string to clear
      proxyPassword: proxyPassword.trim(),  // Send empty string to clear
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
