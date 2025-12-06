import { useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
  Box,
  Paper,
  Typography,
  Button,
  Grid,
  Card,
  CardContent,
  CardActions,
  CircularProgress,
  Alert,
  Chip,
  Divider,
} from '@mui/material';
import { useQuery, useMutation } from '@tanstack/react-query';
import { ArrowBack, Sync as SyncIcon, PlayArrow } from '@mui/icons-material';
import sourcesApi, { SYNC_TASK_TYPES } from '../services/sourcesApi';
import { useAuthStore } from '../stores/authStore';

// Type definitions for error handling
interface ApiErrorResponse {
  response?: {
    data?: {
      message?: string;
    };
  };
}

export default function SourceDetail() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { isAuthenticated } = useAuthStore();
  const [syncingTask, setSyncingTask] = useState<string | null>(null);
  const [syncMessage, setSyncMessage] = useState<{ type: 'success' | 'error'; message: string } | null>(null);

  const { data: source, isLoading, error } = useQuery({
    queryKey: ['source', id],
    queryFn: () => sourcesApi.getSource(Number(id)),
    enabled: isAuthenticated && !!id,
  });

  const syncTaskMutation = useMutation({
    mutationFn: ({ taskType }: { taskType: string }) =>
      sourcesApi.triggerSyncTaskType(Number(id), taskType),
    onMutate: ({ taskType }) => {
      setSyncingTask(taskType);
      setSyncMessage(null);
    },
    onSuccess: () => {
      setSyncMessage({
        type: 'success',
        message: 'Synchronization completed successfully!',
      });
      setSyncingTask(null);
    },
    onError: (error: ApiErrorResponse) => {
      setSyncMessage({
        type: 'error',
        message: error?.response?.data?.message || 'Synchronization failed',
      });
      setSyncingTask(null);
    },
  });

  const syncAllMutation = useMutation({
    mutationFn: () => sourcesApi.triggerSyncAll(Number(id)),
    onMutate: () => {
      setSyncingTask('all');
      setSyncMessage(null);
    },
    onSuccess: () => {
      setSyncMessage({
        type: 'success',
        message: 'Full synchronization completed successfully!',
      });
      setSyncingTask(null);
    },
    onError: (error: ApiErrorResponse) => {
      setSyncMessage({
        type: 'error',
        message: error?.response?.data?.message || 'Full synchronization failed',
      });
      setSyncingTask(null);
    },
  });

  const handleSyncTask = (taskType: string) => {
    syncTaskMutation.mutate({ taskType });
  };

  const handleSyncAll = () => {
    syncAllMutation.mutate();
  };

  if (isLoading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: 400 }}>
        <CircularProgress />
      </Box>
    );
  }

  if (error) {
    return (
      <Box sx={{ p: 2 }}>
        <Button startIcon={<ArrowBack />} onClick={() => navigate('/sources')} sx={{ mb: 2 }}>
          Back to Sources
        </Button>
        <Alert severity="error">Failed to load source</Alert>
      </Box>
    );
  }

  if (!source?.data) {
    return (
      <Box sx={{ p: 2 }}>
        <Button startIcon={<ArrowBack />} onClick={() => navigate('/sources')} sx={{ mb: 2 }}>
          Back to Sources
        </Button>
        <Alert severity="warning">Source not found</Alert>
      </Box>
    );
  }

  const sourceData = source.data;

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', height: '100%', width: '100%', flex: 1, p: 2 }}>
      <Box sx={{ mb: 3, display: 'flex', alignItems: 'center', gap: 2 }}>
        <Button startIcon={<ArrowBack />} onClick={() => navigate('/sources')}>
          Back to Sources
        </Button>
        <Typography variant="h5">{sourceData.name}</Typography>
        <Chip
          label={sourceData.sync_status}
          color={sourceData.sync_status === 'idle' ? 'success' : sourceData.sync_status === 'syncing' ? 'info' : 'error'}
          size="small"
        />
      </Box>

      <Paper sx={{ p: 3, mb: 3 }}>
        <Typography variant="h6" gutterBottom>
          Source Information
        </Typography>
        <Grid container spacing={2}>
          <Grid item xs={12} md={6}>
            <Typography>
              <strong>URL:</strong> {sourceData.url}
            </Typography>
          </Grid>
          <Grid item xs={12} md={6}>
            <Typography>
              <strong>Username:</strong> {sourceData.username}
            </Typography>
          </Grid>
          <Grid item xs={12} md={6}>
            <Typography>
              <strong>Sync Interval:</strong> {sourceData.sync_interval} day{sourceData.sync_interval !== 1 ? 's' : ''}
            </Typography>
          </Grid>
          <Grid item xs={12} md={6}>
            <Typography>
              <strong>Last Sync:</strong> {sourceData.last_sync ? new Date(sourceData.last_sync).toLocaleString() : 'Never'}
            </Typography>
          </Grid>
          <Grid item xs={12} md={6}>
            <Typography>
              <strong>Status:</strong>{' '}
              <Chip
                label={sourceData.is_active ? 'Active' : 'Inactive'}
                color={sourceData.is_active ? 'success' : 'default'}
                size="small"
              />
            </Typography>
          </Grid>
        </Grid>
      </Paper>

      <Box sx={{ mb: 3 }}>
        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
          <Typography variant="h6">Synchronization Tasks</Typography>
          <Button
            variant="contained"
            color="primary"
            startIcon={syncingTask === 'all' ? <CircularProgress size={20} /> : <PlayArrow />}
            onClick={handleSyncAll}
            disabled={!!syncingTask}
          >
            Sync All
          </Button>
        </Box>

        <Grid container spacing={2}>
          {SYNC_TASK_TYPES.map((task) => (
            <Grid item xs={12} sm={6} md={4} key={task.id}>
              <Card>
                <CardContent>
                  <Typography variant="h6" gutterBottom>
                    {task.label}
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    Synchronize {task.label.toLowerCase()} from source
                  </Typography>
                </CardContent>
                <Divider />
                <CardActions>
                  <Button
                    size="small"
                    startIcon={syncingTask === task.id ? <CircularProgress size={16} /> : <SyncIcon />}
                    onClick={() => handleSyncTask(task.id)}
                    disabled={!!syncingTask}
                  >
                    Sync
                  </Button>
                </CardActions>
              </Card>
            </Grid>
          ))}
        </Grid>

        {syncMessage && (
          <Alert severity={syncMessage.type} sx={{ mt: 2 }}>
            {syncMessage.message}
          </Alert>
        )}
      </Box>
    </Box>
  );
}
