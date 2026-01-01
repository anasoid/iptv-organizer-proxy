import { useState, useRef } from 'react';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  Box,
  CircularProgress,
  Alert,
  Tabs,
  Tab,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  Card,
  CardContent,
  Typography,
} from '@mui/material';
import { Download as DownloadIcon, Upload as UploadIcon } from '@mui/icons-material';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import accessControlApi, { type AccessControlExportData } from '../services/accessControlApi';

interface AccessControlModalProps {
  open: boolean;
  onClose: () => void;
  sourceId?: number;
}

interface TabPanelProps {
  children?: React.ReactNode;
  index: number;
  value: number;
}

function TabPanel(props: TabPanelProps) {
  const { children, value, index, ...other } = props;
  return (
    <div
      role="tabpanel"
      hidden={value !== index}
      id={`ac-tabpanel-${index}`}
      aria-labelledby={`ac-tab-${index}`}
      {...other}
    >
      {value === index && <Box sx={{ p: 2 }}>{children}</Box>}
    </div>
  );
}

export default function AccessControlModal({ open, onClose, sourceId }: AccessControlModalProps) {
  const queryClient = useQueryClient();
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [tabValue, setTabValue] = useState(0);
  const [exportFormat, setExportFormat] = useState<'json' | 'yaml'>('json');
  const [importError, setImportError] = useState<string | null>(null);
  const [importSuccess, setImportSuccess] = useState(false);

  // Export mutation
  const exportMutation = useMutation({
    mutationFn: async () => {
      const blob = await accessControlApi.export(sourceId, exportFormat);
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `access-control-${sourceId ? `source-${sourceId}-` : ''}${new Date().toISOString().split('T')[0]}.${exportFormat}`;
      document.body.appendChild(a);
      a.click();
      window.URL.revokeObjectURL(url);
      document.body.removeChild(a);
    },
  });

  // Import mutation
  const importMutation = useMutation({
    mutationFn: async (file: File) => {
      return new Promise<AccessControlExportData>((resolve, reject) => {
        const reader = new FileReader();
        reader.onload = (e) => {
          try {
            const content = e.target?.result as string;
            const data = JSON.parse(content);
            resolve(data);
          } catch {
            reject(new Error('Invalid file format. Please provide a valid JSON file.'));
          }
        };
        reader.onerror = () => {
          reject(new Error('Failed to read file'));
        };
        reader.readAsText(file);
      });
    },
    onSuccess: async (data) => {
      try {
        const result = await accessControlApi.import(data);
        if (result.success) {
          setImportSuccess(true);
          setImportError(null);
          queryClient.invalidateQueries({ queryKey: ['streams'] });
          queryClient.invalidateQueries({ queryKey: ['categories'] });
          setTimeout(() => {
            onClose();
            setImportSuccess(false);
          }, 2000);
        } else {
          setImportError(result.message);
        }
      } catch (err) {
        setImportError(err instanceof Error ? err.message : 'Import failed');
      }
    },
    onError: (err) => {
      setImportError(err instanceof Error ? err.message : 'Import failed');
    },
  });

  const handleFileSelect = (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (file) {
      importMutation.mutate(file);
    }
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle>Access Control Import/Export</DialogTitle>
      <DialogContent>
        <Tabs
          value={tabValue}
          onChange={(_, newValue) => setTabValue(newValue)}
          aria-label="import/export tabs"
          sx={{ mb: 2 }}
        >
          <Tab label="Export" id="ac-tab-0" aria-controls="ac-tabpanel-0" />
          <Tab label="Import" id="ac-tab-1" aria-controls="ac-tabpanel-1" />
        </Tabs>

        {/* Export Tab */}
        <TabPanel value={tabValue} index={0}>
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
            <Typography variant="body2" color="text.secondary">
              Download access control settings for streams and categories as JSON or YAML file.
            </Typography>

            <FormControl fullWidth size="small">
              <InputLabel>Export Format</InputLabel>
              <Select
                value={exportFormat}
                label="Export Format"
                onChange={(e) => setExportFormat(e.target.value as 'json' | 'yaml')}
              >
                <MenuItem value="json">JSON</MenuItem>
                <MenuItem value="yaml">YAML</MenuItem>
              </Select>
            </FormControl>

            {sourceId && (
              <Alert severity="info">
                Exporting access control settings for Source ID: {sourceId}
              </Alert>
            )}

            <Button
              variant="contained"
              startIcon={exportMutation.isPending ? <CircularProgress size={20} /> : <DownloadIcon />}
              onClick={() => exportMutation.mutate()}
              disabled={exportMutation.isPending}
              fullWidth
            >
              Download Settings
            </Button>
          </Box>
        </TabPanel>

        {/* Import Tab */}
        <TabPanel value={tabValue} index={1}>
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
            <Typography variant="body2" color="text.secondary">
              Upload a JSON file containing access control settings to import.
            </Typography>

            {importError && (
              <Alert severity="error">{importError}</Alert>
            )}

            {importSuccess && (
              <Alert severity="success">
                Access control settings imported successfully!
              </Alert>
            )}

            <Card sx={{ bgcolor: 'action.hover' }}>
              <CardContent sx={{ p: 2, textAlign: 'center' }}>
                <Box
                  sx={{
                    border: '2px dashed #ccc',
                    borderRadius: 1,
                    p: 3,
                    cursor: 'pointer',
                    '&:hover': { borderColor: 'primary.main' },
                  }}
                  onClick={() => fileInputRef.current?.click()}
                >
                  <UploadIcon sx={{ fontSize: 48, color: 'text.secondary', mb: 1 }} />
                  <Typography variant="body2" color="text.secondary">
                    Click to select or drag and drop
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
                    JSON format required
                  </Typography>
                  <input
                    ref={fileInputRef}
                    type="file"
                    accept=".json"
                    onChange={handleFileSelect}
                    style={{ display: 'none' }}
                  />
                </Box>
              </CardContent>
            </Card>

            {importMutation.isPending && (
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                <CircularProgress size={20} />
                <Typography variant="body2">Processing import...</Typography>
              </Box>
            )}
          </Box>
        </TabPanel>
      </DialogContent>

      <DialogActions>
        <Button onClick={onClose}>Close</Button>
      </DialogActions>
    </Dialog>
  );
}
