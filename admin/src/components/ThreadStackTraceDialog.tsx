import {
  Alert,
  Box,
  Chip,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Button,
  FormControlLabel,
  Grid,
  Switch,
  Typography,
} from '@mui/material';
import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import jvmMetricsApi from '../services/jvmMetricsApi';

interface ThreadStackTraceDialogProps {
  open: boolean;
  threadId: number | null;
  threadName?: string | null;
  onClose: () => void;
}

function fmtMs(value: number): string {
  return value >= 0 ? `${value} ms` : 'N/A';
}

function fmtCount(value: number): string {
  return value >= 0 ? String(value) : 'N/A';
}

export default function ThreadStackTraceDialog({
  open,
  threadId,
  threadName,
  onClose,
}: ThreadStackTraceDialogProps) {
  const [wrapLines, setWrapLines] = useState(true);
  const { data, isLoading, isError } = useQuery({
    queryKey: ['threadStackTrace', threadId],
    queryFn: () => jvmMetricsApi.getThreadStackTrace(threadId as number),
    enabled: open && threadId !== null,
  });

  return (
    <Dialog
      open={open}
      onClose={onClose}
      fullWidth
      maxWidth="md"
      PaperProps={{
        sx: {
          resize: 'both',
          overflow: 'auto',
          minWidth: 700,
          minHeight: 480,
          maxWidth: '95vw',
          maxHeight: '95vh',
        },
      }}
    >
      <DialogTitle>
        Stack Trace{threadName ? ` — ${threadName}` : ''}
      </DialogTitle>
      <DialogContent dividers>
        <Box sx={{ display: 'flex', justifyContent: 'flex-end', mb: 1 }}>
          <FormControlLabel
            control={
              <Switch
                size="small"
                checked={wrapLines}
                onChange={(_, checked) => setWrapLines(checked)}
              />
            }
            label={<Typography variant="body2">Wrap lines</Typography>}
          />
        </Box>
        {threadId === null && (
          <Alert severity="warning">Thread id is not available for this entry.</Alert>
        )}
        {isLoading && (
          <Box sx={{ display: 'flex', justifyContent: 'center', py: 3 }}>
            <CircularProgress size={24} />
          </Box>
        )}
        {isError && (
          <Alert severity="error">Failed to load stack trace.</Alert>
        )}
        {!isLoading && !isError && data && (
          <>
            <Grid container spacing={1.5} sx={{ mb: 2 }}>
              <Grid size={{ xs: 12, sm: 6 }}>
                <Typography variant="caption" color="text.secondary">Thread</Typography>
                <Typography variant="body2" sx={{ fontFamily: 'monospace' }}>
                  #{data.threadId} {data.name}
                </Typography>
              </Grid>
              <Grid size={{ xs: 12, sm: 6 }}>
                <Typography variant="caption" color="text.secondary">State</Typography>
                <Box>
                  <Chip
                    size="small"
                    label={data.state}
                    color={data.state === 'BLOCKED' ? 'error' : data.state === 'RUNNABLE' ? 'success' : 'default'}
                  />
                </Box>
              </Grid>
              <Grid size={{ xs: 6, sm: 3 }}>
                <Typography variant="caption" color="text.secondary">CPU Time</Typography>
                <Typography variant="body2">{fmtMs(data.cpuTimeMs)}</Typography>
              </Grid>
              <Grid size={{ xs: 6, sm: 3 }}>
                <Typography variant="caption" color="text.secondary">User Time</Typography>
                <Typography variant="body2">{fmtMs(data.userTimeMs)}</Typography>
              </Grid>
              <Grid size={{ xs: 6, sm: 3 }}>
                <Typography variant="caption" color="text.secondary">Blocked</Typography>
                <Typography variant="body2">
                  {fmtCount(data.blockedCount)} ({fmtMs(data.blockedTimeMs)})
                </Typography>
              </Grid>
              <Grid size={{ xs: 6, sm: 3 }}>
                <Typography variant="caption" color="text.secondary">Waited</Typography>
                <Typography variant="body2">
                  {fmtCount(data.waitedCount)} ({fmtMs(data.waitedTimeMs)})
                </Typography>
              </Grid>
              <Grid size={{ xs: 12, sm: 6 }}>
                <Typography variant="caption" color="text.secondary">Current Lock</Typography>
                <Typography variant="body2" sx={{ fontFamily: 'monospace', wordBreak: 'break-all' }}>
                  {data.lockName || 'None'}
                </Typography>
              </Grid>
              <Grid size={{ xs: 12, sm: 6 }}>
                <Typography variant="caption" color="text.secondary">Lock Owner</Typography>
                <Typography variant="body2" sx={{ fontFamily: 'monospace', wordBreak: 'break-all' }}>
                  {data.lockOwnerName ? `${data.lockOwnerName} (#${data.lockOwnerId ?? '?'})` : 'None'}
                </Typography>
              </Grid>
              <Grid size={{ xs: 12 }}>
                <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
                  {data.daemon && <Chip size="small" label="daemon" variant="outlined" />}
                  {data.suspended && <Chip size="small" label="suspended" color="warning" />}
                  {data.inNative && <Chip size="small" label="in-native" color="info" />}
                  {data.deadlocked && <Chip size="small" label="deadlocked" color="error" />}
                  {data.lockedMonitors.length > 0 && (
                    <Chip size="small" label={`locked monitors: ${data.lockedMonitors.length}`} />
                  )}
                  {data.lockedSynchronizers.length > 0 && (
                    <Chip size="small" label={`locked synchronizers: ${data.lockedSynchronizers.length}`} />
                  )}
                </Box>
              </Grid>
            </Grid>
            <Box
              component="pre"
              sx={{
                m: 0,
                fontFamily: 'monospace',
                fontSize: '0.8rem',
                whiteSpace: wrapLines ? 'pre-wrap' : 'pre',
                wordBreak: wrapLines ? 'break-word' : 'normal',
                overflowX: wrapLines ? 'hidden' : 'auto',
              }}
            >
              {data.stackTrace.join('\n')}
            </Box>
          </>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Close</Button>
      </DialogActions>
    </Dialog>
  );
}
