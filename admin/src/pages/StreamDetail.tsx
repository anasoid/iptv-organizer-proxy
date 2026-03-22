import { useParams, useNavigate } from 'react-router-dom';
import { useState } from 'react';
import {
  Box,
  Card,
  CardMedia,
  CircularProgress,
  Grid,
  Typography,
  Chip,
  Alert,
  Button,
  Divider,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableRow,
  Paper,
  IconButton,
  Tooltip,
  Snackbar,
  ButtonGroup,
} from '@mui/material';
import { ArrowBack as ArrowBackIcon, ContentCopy as ContentCopyIcon, CheckCircle as CheckCircleIcon, Block as BlockIcon } from '@mui/icons-material';
import { useQuery, useMutation } from '@tanstack/react-query';
import { useAuthStore } from '../stores/authStore';
import streamsApi from '../services/streamsApi';
import categoriesApi from '../services/categoriesApi';

export default function StreamDetail() {
  const { id, type } = useParams<{ id: string; type: string }>();
  const navigate = useNavigate();
  const { isAuthenticated } = useAuthStore();
  const [snackbarMessage, setSnackbarMessage] = useState('');
  const [snackbarOpen, setSnackbarOpen] = useState(false);

  const streamId = id ? parseInt(id, 10) : null;
  const streamType = (type || 'live') as 'live' | 'vod' | 'series';

  // Fetch stream details
  const { data: streamData, isLoading: isLoadingStream, error: streamError, refetch: refetchStream } = useQuery({
    queryKey: ['stream', streamId, streamType],
    queryFn: () => (streamId ? streamsApi.getStream(streamId, streamType) : Promise.resolve(null)),
    enabled: isAuthenticated && streamId !== null,
  });

  const stream = streamData?.data;

  // Mutation for updating allow_deny
  const updateAllowDenyMutation = useMutation({
    mutationFn: (allowDeny: string | null) =>
      streamsApi.updateAllowDeny(streamId!, allowDeny, streamType),
    onSuccess: () => {
      setSnackbarMessage('Allow/Deny status updated successfully');
      setSnackbarOpen(true);
      refetchStream();
    },
    onError: () => {
      setSnackbarMessage('Failed to update Allow/Deny status');
      setSnackbarOpen(true);
    },
  });

  // Fetch source ID from stream to get all categories as fallback
  const sourceId = stream?.sourceId;

  // Fetch category by external ID (stream.categoryId is the external ID from upstream Xtream)
  // Type comes from route param-derived streamType (stream payload does not include a `type` field).
  const { data: category } = useQuery({
    queryKey: ['category', stream?.categoryId, sourceId, streamType],
    queryFn: async () => {
      if (!stream?.categoryId || !sourceId) {
        console.log('No category_id or source_id on stream');
        return Promise.resolve(null);
      }
      console.log('Fetching category by external ID:', {
        external_id: stream.categoryId,
        source_id: sourceId,
        type: streamType,
      });
      try {
        const result = await categoriesApi.getCategoryByExternalId(
          Number(stream.categoryId),
          sourceId,
          streamType,
        );
        console.log('Category found by external ID:', result.data.name);
        return result.data;  // Return just the Category object, not the response wrapper
      } catch (err) {
        console.log('Error fetching category by external ID:', err);
        return null;
      }
    },
    enabled: isAuthenticated && !!stream?.categoryId && !!sourceId,
  });

  const getTypeColor = (type: string): 'default' | 'primary' | 'secondary' | 'error' | 'warning' | 'info' | 'success' => {
    switch (type.toLowerCase()) {
      case 'live':
        return 'primary';
      case 'vod':
      case 'movie':
        return 'secondary';
      case 'series':
        return 'warning';
      default:
        return 'default';
    }
  };

  const getBackPath = (): string => {
    const referrer = sessionStorage.getItem('streamDetailReferrer');
    if (referrer) {
      sessionStorage.removeItem('streamDetailReferrer');
      return referrer;
    }

    // Default back paths based on type
    switch (streamType) {
      case 'live':
        return '/live-streams';
      case 'vod':
        return '/vod-streams';
      case 'series':
        return '/series';
      default:
        return '/live-streams';
    }
  };

  if (isLoadingStream) {
    return (
      <Box sx={{ p: 3, display: 'flex', justifyContent: 'center' }}>
        <CircularProgress />
      </Box>
    );
  }

  if (streamError || !stream) {
    return (
      <Box sx={{ p: 3 }}>
        <Button
          startIcon={<ArrowBackIcon />}
          onClick={() => navigate(getBackPath())}
          sx={{ mb: 2 }}
        >
          Go Back
        </Button>
        <Alert severity="error">Failed to load stream details.</Alert>
      </Box>
    );
  }

  const streamIcon = stream.data?.stream_icon || stream.data?.cover;
  const duration = stream.data?.duration;
  const episodes = stream.data?.episodes;
  const seasons = stream.data?.seasons;

  const formatDuration = (seconds: number): string => {
    if (!seconds) return 'N/A';
    const hours = Math.floor(seconds / 3600);
    const minutes = Math.floor((seconds % 3600) / 60);
    if (hours > 0) return `${hours}h ${minutes}m`;
    return `${minutes}m`;
  };

  const handleCopyToClipboard = (text: string, message: string = 'Copied to clipboard!') => {
    navigator.clipboard.writeText(text).then(() => {
      setSnackbarMessage(message);
      setSnackbarOpen(true);
    });
  };

  return (
    <Box sx={{ p: 3 }}>
      {/* Header */}
      <Button
        startIcon={<ArrowBackIcon />}
        onClick={() => navigate(getBackPath())}
        sx={{ mb: 2 }}
      >
        Go Back
      </Button>

      {/* Stream Info Card */}
      <Card sx={{ mb: 3 }}>
        <Grid container spacing={0}>
          {/* Left Section: Detail Info + Metadata */}
          <Grid item xs={12} sm={streamIcon ? 8 : 12} md={streamIcon ? 10 : 12}>
            {/* Detail Info */}
            <Box sx={{ p: 3 }}>
              {/* Header with Type Badge and Category */}
              <Box sx={{ display: 'flex', gap: 1, alignItems: 'center', mb: 2, flexWrap: 'wrap' }}>
                <Typography variant="h4" sx={{ flex: '1 1 auto' }}>
                  {stream.name}
                </Typography>
                {stream.categoryId && (
                  <Chip
                    label={category?.name || `Category ${stream.categoryId}`}
                    variant="outlined"
                    color={category ? 'default' : 'warning'}
                    size="medium"
                    onClick={() => {
                      // Store the selected category and navigate to stream listing
                      sessionStorage.setItem('streamDetailReferrer', window.location.pathname);
                      sessionStorage.setItem('filterByCategoryId', String(stream.categoryId));
                      const streamPageMap: Record<string, string> = {
                        live: '/live-streams',
                        vod: '/vod-streams',
                        series: '/series',
                      };
                      navigate(streamPageMap[streamType] || '/live-streams');
                    }}
                    sx={{ cursor: 'pointer' }}
                  />
                )}
                <Chip
                  label={streamType.toUpperCase()}
                  color={getTypeColor(streamType)}
                  size="medium"
                />
              </Box>

              <Divider sx={{ my: 2 }} />

              {/* Grid of Details */}
              <Grid container spacing={2}>
                <Grid item xs={12} sm={6}>
                  <Typography variant="subtitle2" sx={{ fontWeight: 600, color: 'text.secondary' }}>
                    Stream ID
                  </Typography>
                  <Typography variant="body1">{stream.externalId}</Typography>
                </Grid>

                <Grid item xs={12} sm={6}>
                  <Typography variant="subtitle2" sx={{ fontWeight: 600, color: 'text.secondary' }}>
                    Database ID
                  </Typography>
                  <Typography variant="body1">{stream.id}</Typography>
                </Grid>

                {category && (
                  <Grid item xs={12} sm={6}>
                    <Typography variant="subtitle2" sx={{ fontWeight: 600, color: 'text.secondary' }}>
                      Category
                    </Typography>
                    <Typography
                      variant="body1"
                      sx={{ cursor: 'pointer', color: 'primary.main', textDecoration: 'hover' }}
                      onClick={() => {
                        sessionStorage.setItem('streamDetailReferrer', window.location.pathname);
                        navigate(`/categories/${category.id}`);
                      }}
                    >
                      {category.name}
                    </Typography>
                  </Grid>
                )}

                {stream.isAdult && (
                  <Grid item xs={12} sm={6}>
                    <Chip label="Adult Content" color="error" />
                  </Grid>
                )}

                <Grid item xs={12} sm={6}>
                  <Typography variant="subtitle2" sx={{ fontWeight: 600, color: 'text.secondary', mb: 1 }}>
                    Access Control
                  </Typography>
                  <ButtonGroup variant="outlined" size="small">
                    <Button
                      startIcon={<CheckCircleIcon />}
                      variant={stream.allowDeny === 'allow' ? 'contained' : 'outlined'}
                      color={stream.allowDeny === 'allow' ? 'success' : 'inherit'}
                      onClick={() => updateAllowDenyMutation.mutate('allow')}
                      disabled={updateAllowDenyMutation.isPending}
                    >
                      Allow
                    </Button>
                    <Button
                      startIcon={<BlockIcon />}
                      variant={stream.allowDeny === 'deny' ? 'contained' : 'outlined'}
                      color={stream.allowDeny === 'deny' ? 'error' : 'inherit'}
                      onClick={() => updateAllowDenyMutation.mutate('deny')}
                      disabled={updateAllowDenyMutation.isPending}
                    >
                      Deny
                    </Button>
                    <Button
                      variant={stream.allowDeny === null ? 'contained' : 'outlined'}
                      onClick={() => updateAllowDenyMutation.mutate(null)}
                      disabled={updateAllowDenyMutation.isPending}
                    >
                      Default
                    </Button>
                  </ButtonGroup>
                </Grid>

                {duration && (
                  <Grid item xs={12} sm={6}>
                    <Typography variant="subtitle2" sx={{ fontWeight: 600, color: 'text.secondary' }}>
                      Duration
                    </Typography>
                    <Typography variant="body1">{formatDuration(Number(duration))}</Typography>
                  </Grid>
                )}

                {seasons && (
                  <Grid item xs={12} sm={6}>
                    <Typography variant="subtitle2" sx={{ fontWeight: 600, color: 'text.secondary' }}>
                      Seasons
                    </Typography>
                    <Typography variant="body1">
                      {typeof seasons === 'object' ? Object.keys(seasons).length : seasons}
                    </Typography>
                  </Grid>
                )}

                {episodes && (
                  <Grid item xs={12} sm={6}>
                    <Typography variant="subtitle2" sx={{ fontWeight: 600, color: 'text.secondary' }}>
                      Episodes
                    </Typography>
                    <Typography variant="body1">{episodes}</Typography>
                  </Grid>
                )}

                {stream.labels && (
                  <Grid item xs={12}>
                    <Typography variant="subtitle2" sx={{ fontWeight: 600, color: 'text.secondary', mb: 1 }}>
                      Labels
                    </Typography>
                    <Box sx={{ display: 'flex', gap: 0.5, flexWrap: 'wrap' }}>
                      {stream.labels.split(',').map((label, idx) => (
                        <Chip key={idx} label={label.trim()} size="small" variant="outlined" />
                      ))}
                    </Box>
                  </Grid>
                )}

                <Grid item xs={12}>
                  <Typography variant="subtitle2" sx={{ fontWeight: 600, color: 'text.secondary' }}>
                    Created At
                  </Typography>
                  <Typography variant="body2">
                    {new Date(stream.created_at).toLocaleDateString('en-US', {
                      year: 'numeric',
                      month: 'short',
                      day: 'numeric',
                      hour: '2-digit',
                      minute: '2-digit',
                    })}
                  </Typography>
                </Grid>

                {stream.updated_at && (
                  <Grid item xs={12}>
                    <Typography variant="subtitle2" sx={{ fontWeight: 600, color: 'text.secondary' }}>
                      Updated At
                    </Typography>
                    <Typography variant="body2">
                      {new Date(stream.updated_at).toLocaleDateString('en-US', {
                        year: 'numeric',
                        month: 'short',
                        day: 'numeric',
                        hour: '2-digit',
                        minute: '2-digit',
                      })}
                    </Typography>
                  </Grid>
                )}
              </Grid>
            </Box>

            {/* Metadata Table - Below Detail Info */}
            {stream.data && Object.keys(stream.data).length > 0 && (
              <Box sx={{ p: 3, pt: 0, backgroundColor: '#fafafa' }}>
                <Typography variant="subtitle2" sx={{ fontWeight: 600, mb: 2 }}>
                  Metadata
                </Typography>
                <TableContainer sx={{ maxHeight: 250 }}>
                  <Table size="small">
                    <TableBody>
                      {Object.entries(stream.data).map(([key, value]) => {
                        // Skip certain fields we already display
                        if (['stream_icon', 'cover', 'url', 'duration', 'episodes', 'seasons'].includes(key)) {
                          return null;
                        }

                        let displayValue: string;
                        if (typeof value === 'object') {
                          displayValue = JSON.stringify(value, null, 2);
                        } else {
                          displayValue = String(value);
                        }

                        return (
                          <TableRow key={key}>
                            <TableCell sx={{ verticalAlign: 'top', fontWeight: 500, fontSize: '0.875rem' }}>
                              {key}
                            </TableCell>
                            <TableCell sx={{ fontSize: '0.875rem' }}>
                              <Typography
                                variant="body2"
                                sx={{
                                  fontFamily: 'monospace',
                                  wordBreak: 'break-word',
                                }}
                              >
                                {displayValue.length > 80 ? displayValue.substring(0, 80) + '...' : displayValue}
                              </Typography>
                            </TableCell>
                          </TableRow>
                        );
                      })}
                    </TableBody>
                  </Table>
                </TableContainer>
              </Box>
            )}
          </Grid>

          {/* Right Section: Image Only */}
          {streamIcon && (
            <Grid item xs={12} sm={4} md={2} sx={{ display: 'flex', alignItems: 'stretch' }}>
              <CardMedia
                component="img"
                image={streamIcon}
                alt={stream.name}
                sx={{
                  width: '100%',
                  height: '100%',
                  minHeight: 150,
                  objectFit: 'cover',
                  backgroundColor: '#f0f0f0',
                }}
              />
            </Grid>
          )}
        </Grid>
      </Card>

      {/* Raw Data JSON */}
      {stream.data && Object.keys(stream.data).length > 0 && (
        <Card sx={{ mb: 3, p: 3 }}>
          <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
            <Typography variant="h6" sx={{ fontWeight: 600 }}>
              Raw Data (JSON)
            </Typography>
            <Tooltip title="Copy to clipboard">
              <IconButton
                size="small"
                onClick={() =>
                  handleCopyToClipboard(
                    JSON.stringify(stream.data, null, 2),
                    'Data copied to clipboard!'
                  )
                }
              >
                <ContentCopyIcon fontSize="small" />
              </IconButton>
            </Tooltip>
          </Box>

          <Paper
            variant="outlined"
            sx={{
              p: 2,
              backgroundColor: '#f5f5f5',
              overflow: 'auto',
              maxHeight: 400,
              fontFamily: 'monospace',
              fontSize: '0.875rem',
              lineHeight: 1.6,
              whiteSpace: 'pre-wrap',
              wordBreak: 'break-word',
            }}
          >
            {JSON.stringify(stream.data, null, 2)}
          </Paper>
        </Card>
      )}


      {/* Snackbar for copy notification */}
      <Snackbar
        open={snackbarOpen}
        autoHideDuration={2000}
        onClose={() => setSnackbarOpen(false)}
        message={snackbarMessage}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'left' }}
      />
    </Box>
  );
}
