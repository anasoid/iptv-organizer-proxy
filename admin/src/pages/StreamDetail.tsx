import { useParams, useNavigate } from 'react-router-dom';
import { useState } from 'react';
import {
  Box,
  Card,
  CardMedia,
  CircularProgress,
  Dialog,
  DialogContent,
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

function parseStreamData(data: unknown): Record<string, unknown> | null {
  if (!data) {
    return null;
  }

  if (typeof data === 'object' && !Array.isArray(data)) {
    return data as Record<string, unknown>;
  }

  if (typeof data === 'string') {
    try {
      const parsed = JSON.parse(data);
      if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) {
        return parsed as Record<string, unknown>;
      }
    } catch {
      return null;
    }
  }

  return null;
}

function formatRawStreamData(data: unknown): string {
  if (data === null || data === undefined) {
    return '';
  }

  if (typeof data === 'string') {
    try {
      return JSON.stringify(JSON.parse(data), null, 2);
    } catch {
      return data;
    }
  }

  try {
    return JSON.stringify(data, null, 2);
  } catch {
    return String(data);
  }
}

export default function StreamDetail() {
  const { id, type } = useParams<{ id: string; type: string }>();
  const navigate = useNavigate();
  const { isAuthenticated } = useAuthStore();
  const [snackbarMessage, setSnackbarMessage] = useState('');
  const [snackbarOpen, setSnackbarOpen] = useState(false);
  const [imagePreviewOpen, setImagePreviewOpen] = useState(false);

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

  const metadata = parseStreamData(stream.data);
  const rawDataText = formatRawStreamData(stream.data);
  const hasMetadata = !!metadata && Object.keys(metadata).length > 0;
  const hasRawData = rawDataText.length > 0;

  const streamIcon = metadata?.stream_icon || metadata?.cover;
  const duration = metadata?.duration;
  const episodes = metadata?.episodes;
  const seasons = metadata?.seasons;

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

  const handleImagePreviewOpen = () => {
    if (streamIcon) {
      setImagePreviewOpen(true);
    }
  };

  const handleImageKeyDown = (event: React.KeyboardEvent<HTMLDivElement>) => {
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault();
      handleImagePreviewOpen();
    }
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
        <Grid
          container
          spacing={0}
          alignItems="flex-start"
          direction="row"
          sx={{ flexWrap: { xs: 'wrap', sm: 'nowrap' } }}
        >
          {/* Main Section: Detail Info + Metadata */}
          <Grid item xs={12} sm={streamIcon ? 7 : 12} md={streamIcon ? 8 : 12} lg={streamIcon ? 9 : 12} sx={{ order: 1 }}>
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
            {hasMetadata && metadata && (
              <Box sx={{ p: 3, pt: 0, backgroundColor: '#fafafa' }}>
                <Typography variant="subtitle2" sx={{ fontWeight: 600, mb: 2 }}>
                  Metadata
                </Typography>
                <TableContainer sx={{ maxHeight: 250 }}>
                  <Table size="small" sx={{ tableLayout: 'fixed', width: '100%' }}>
                    <TableBody>
                      {Object.entries(metadata).map(([key, value]) => {
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
                            <TableCell
                              sx={{
                                width: 180,
                                minWidth: 180,
                                maxWidth: 180,
                                verticalAlign: 'top',
                                fontWeight: 500,
                                fontSize: '0.875rem',
                                whiteSpace: 'nowrap',
                              }}
                            >
                              {key}
                            </TableCell>
                            <TableCell sx={{ width: 'calc(100% - 180px)', fontSize: '0.875rem' }}>
                              <Typography
                                variant="body2"
                                sx={{
                                  fontFamily: 'monospace',
                                  whiteSpace: 'pre-wrap',
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

          {streamIcon && (
            <Grid item xs={12} sm={5} md={4} lg={3} sx={{ order: 2, flexShrink: 0 }}>
              <Box sx={{ p: 3, pt: { xs: 0, sm: 3 }, display: 'flex', justifyContent: { xs: 'flex-start', sm: 'flex-end' } }}>
                <Box
                  onClick={handleImagePreviewOpen}
                  onKeyDown={handleImageKeyDown}
                  role="button"
                  tabIndex={0}
                  aria-label={`Open full size image for ${stream.name}`}
                  sx={{
                    width: '100%',
                    maxWidth: { xs: 220, sm: 280, md: 320 },
                    borderRadius: 2,
                    overflow: 'hidden',
                    cursor: 'pointer',
                    boxShadow: 2,
                    transition: 'transform 0.2s ease, box-shadow 0.2s ease',
                    '&:hover': {
                      transform: 'scale(1.02)',
                      boxShadow: 4,
                    },
                    '&:focus-visible': {
                      outline: '2px solid',
                      outlineColor: 'primary.main',
                      outlineOffset: 2,
                    },
                  }}
                >
                  <CardMedia
                    component="img"
                    image={streamIcon}
                    alt={stream.name}
                    sx={{
                      width: '100%',
                      height: 'auto',
                      maxHeight: { xs: 320, sm: 420 },
                      objectFit: 'cover',
                      display: 'block',
                      backgroundColor: '#f0f0f0',
                    }}
                  />
                </Box>
              </Box>
            </Grid>
          )}
        </Grid>
      </Card>

      {/* Raw Data JSON */}
      {hasRawData && (
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
                    rawDataText,
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
            {rawDataText}
          </Paper>
        </Card>
      )}

      <Dialog
        open={imagePreviewOpen}
        onClose={() => setImagePreviewOpen(false)}
        maxWidth="lg"
        fullWidth
      >
        <DialogContent
          sx={{
            p: 2,
            display: 'flex',
            justifyContent: 'center',
            alignItems: 'center',
            backgroundColor: '#111',
          }}
        >
          {streamIcon && (
            <Box
              component="img"
              src={streamIcon}
              alt={stream.name}
              sx={{
                maxWidth: '100%',
                maxHeight: '80vh',
                objectFit: 'contain',
                borderRadius: 1,
              }}
            />
          )}
        </DialogContent>
      </Dialog>


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
