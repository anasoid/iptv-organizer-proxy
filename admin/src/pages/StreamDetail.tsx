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
  TableHead,
  TableRow,
  Paper,
  IconButton,
  Tooltip,
  Snackbar,
  ButtonGroup,
  Tabs,
  Tab,
} from '@mui/material';
import { ArrowBack as ArrowBackIcon, ContentCopy as ContentCopyIcon, CheckCircle as CheckCircleIcon, Block as BlockIcon } from '@mui/icons-material';
import { useQuery, useMutation } from '@tanstack/react-query';
import { useAuthStore } from '../stores/authStore';
import streamsApi, { type Stream } from '../services/streamsApi';
import categoriesApi from '../services/categoriesApi';
import sourcesApi from '../services/sourcesApi';
import { getCategoryDisplayName } from '../utils/categoryDisplayName';
import { formatDisplayDate } from '../utils/dateFormat';

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
      id={`stream-tabpanel-${index}`}
      aria-labelledby={`stream-tab-${index}`}
      {...other}
    >
      {value === index && <Box sx={{ p: 3 }}>{children}</Box>}
    </div>
  );
}

export default function StreamDetail() {
  const { id, type } = useParams<{ id: string; type: string }>();
  const navigate = useNavigate();
  const { isAuthenticated } = useAuthStore();
  const [snackbarMessage, setSnackbarMessage] = useState('');
  const [snackbarOpen, setSnackbarOpen] = useState(false);
  const [imagePreviewOpen, setImagePreviewOpen] = useState(false);
  const [tabValue, setTabValue] = useState(0);

  const streamId = id ? parseInt(id, 10) : null;
  const streamType = type === 'vod' || type === 'series' || type === 'live' ? type : 'live';

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

  // Fetch all sources for TMDB-linked streams
  const { data: allSources } = useQuery({
    queryKey: ['sources'],
    queryFn: () => sourcesApi.getSources(1, 1000),
    enabled: isAuthenticated,
  });

  // Fetch streams with the same TMDB ID from all sources
  const { data: tmdbLinkedStreams } = useQuery({
    queryKey: ['streams-by-tmdb', stream?.tmdb, streamType],
    queryFn: async () => {
      if (!stream?.tmdb || !allSources?.data) {
        return [];
      }

      try {
        const linkedStreams: Stream[] = [];

        // Query each source for streams with the same TMDB ID
        for (const source of allSources.data) {
          try {
            const response = await streamsApi.getStreams(
              source.id,
              streamType,
              undefined,
              1,
              1000,
              undefined,
              undefined,
              { tmdb: stream.tmdb }
            );

            if (response.data && response.data.length > 0) {
              linkedStreams.push(...response.data);
            }
          } catch (error) {
            console.error(`Error fetching streams with TMDB ${stream.tmdb} from source ${source.id}:`, error);
          }
        }

        // Remove the current stream from the list
        return linkedStreams.filter((s) => s.id !== stream.id);
      } catch (error) {
        console.error('Error fetching TMDB-linked streams:', error);
        return [];
      }
    },
    enabled: isAuthenticated && !!stream?.tmdb && !!allSources?.data,
  });

  const { data: linkedCategoryNames } = useQuery({
    queryKey: [
      'linked-stream-category-names',
      streamType,
      (tmdbLinkedStreams ?? []).map((linkedStream) => `${linkedStream.sourceId}:${linkedStream.categoryId}`).sort(),
    ],
    queryFn: async () => {
      if (!tmdbLinkedStreams || tmdbLinkedStreams.length === 0) {
        return {} as Record<string, string>;
      }

      const uniqueStreamsWithCategories = Array.from(
        new Map(
          tmdbLinkedStreams
            .filter((linkedStream) => linkedStream.categoryId !== null)
            .map((linkedStream) => [`${linkedStream.sourceId}:${linkedStream.categoryId}`, linkedStream])
        ).values()
      );

      const entries = await Promise.all(
        uniqueStreamsWithCategories.map(async (linkedStream) => {
          const key = `${linkedStream.sourceId}:${linkedStream.categoryId}`;

          try {
            const result = await categoriesApi.getCategoryByExternalId(
              Number(linkedStream.categoryId),
              linkedStream.sourceId,
              streamType,
            );

            return [key, getCategoryDisplayName(result.data)] as const;
          } catch {
            return [key, `Category ${linkedStream.categoryId}`] as const;
          }
        })
      );

      return Object.fromEntries(entries);
    },
    enabled: isAuthenticated && !!tmdbLinkedStreams?.length,
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

  const streamIcon =
    typeof metadata?.stream_icon === 'string'
      ? metadata.stream_icon
      : typeof metadata?.cover === 'string'
        ? metadata.cover
        : null;
  const duration = metadata?.duration;
  const episodes = metadata?.episodes;
  const seasons = metadata?.seasons;
  const rawDataTabIndex = stream.tmdb ? 2 : 1;

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

                {stream.releaseDate && (
                  <Grid item xs={12} sm={6}>
                    <Typography variant="subtitle2" sx={{ fontWeight: 600, color: 'text.secondary' }}>
                      Release Date
                    </Typography>
                    <Typography variant="body1">{formatDisplayDate(stream.releaseDate)}</Typography>
                  </Grid>
                )}

                {stream.rating !== null && stream.rating !== undefined && (
                  <Grid item xs={12} sm={6}>
                    <Typography variant="subtitle2" sx={{ fontWeight: 600, color: 'text.secondary' }}>
                      Rating
                    </Typography>
                    <Typography variant="body1">{stream.rating.toFixed(1)}</Typography>
                  </Grid>
                )}

                {stream.tmdb && (
                  <Grid item xs={12} sm={6}>
                    <Typography variant="subtitle2" sx={{ fontWeight: 600, color: 'text.secondary' }}>
                      TMDb ID
                    </Typography>
                    <Typography variant="body1">{stream.tmdb}</Typography>
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
                    {new Date(stream.createdAt).toLocaleDateString('en-US', {
                      year: 'numeric',
                      month: 'short',
                      day: 'numeric',
                      hour: '2-digit',
                      minute: '2-digit',
                    })}
                  </Typography>
                </Grid>

                {stream.updatedAt && (
                  <Grid item xs={12}>
                    <Typography variant="subtitle2" sx={{ fontWeight: 600, color: 'text.secondary' }}>
                      Updated At
                    </Typography>
                    <Typography variant="body2">
                      {new Date(stream.updatedAt).toLocaleDateString('en-US', {
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

      {/* Tabs for Metadata and TMDB Linked Streams */}
      <Card sx={{ mb: 3 }}>
        <Box sx={{ borderBottom: 1, borderColor: 'divider' }}>
          <Tabs
            value={tabValue}
            onChange={(_, newValue) => setTabValue(newValue)}
            aria-label="stream details tabs"
          >
            <Tab label="Metadata" id="stream-tab-0" aria-controls="stream-tabpanel-0" />
            {stream.tmdb && <Tab label={`Same TMDB ID (${tmdbLinkedStreams?.length || 0})`} id="stream-tab-1" aria-controls="stream-tabpanel-1" />}
            {hasRawData && <Tab label="Raw Data" id={`stream-tab-${rawDataTabIndex}`} aria-controls={`stream-tabpanel-${rawDataTabIndex}`} />}
          </Tabs>
        </Box>

        {/* Metadata Tab */}
        <TabPanel value={tabValue} index={0}>
          {hasMetadata && metadata ? (
            <TableContainer sx={{ maxHeight: 400 }}>
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
                              overflowWrap: 'anywhere',
                              wordBreak: 'break-word',
                            }}
                          >
                            {displayValue}
                          </Typography>
                        </TableCell>
                      </TableRow>
                    );
                  })}
                </TableBody>
              </Table>
            </TableContainer>
          ) : (
            <Typography variant="body2" color="text.secondary">
              No metadata available
            </Typography>
          )}
        </TabPanel>

        {/* TMDB Linked Streams Tab */}
        {stream.tmdb && (
          <TabPanel value={tabValue} index={1}>
            {tmdbLinkedStreams && tmdbLinkedStreams.length > 0 ? (
              <Box>
                <Typography variant="subtitle2" sx={{ fontWeight: 600, mb: 2 }}>
                  Streams with the same TMDB ID ({stream.tmdb}) across different sources:
                </Typography>
                <TableContainer>
                  <Table size="small">
                    <TableHead>
                      <TableRow sx={{ backgroundColor: '#f5f5f5' }}>
                        <TableCell sx={{ fontWeight: 600 }}>Stream Name</TableCell>
                        <TableCell sx={{ fontWeight: 600 }}>Type</TableCell>
                        <TableCell sx={{ fontWeight: 600 }}>Source</TableCell>
                        <TableCell sx={{ fontWeight: 600 }}>Category</TableCell>
                        <TableCell sx={{ fontWeight: 600 }}>Action</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {tmdbLinkedStreams.map((linkedStream) => {
                        const sourceInfo = allSources?.data?.find((s) => s.id === linkedStream.sourceId);
                        return (
                          <TableRow key={linkedStream.id}>
                            <TableCell>{linkedStream.name}</TableCell>
                            <TableCell>
                              <Chip label={streamType.toUpperCase()} size="small" color={getTypeColor(streamType)} />
                            </TableCell>
                            <TableCell>{sourceInfo?.name || `Source ${linkedStream.sourceId}`}</TableCell>
                            <TableCell>
                              {linkedStream.categoryId
                                ? linkedCategoryNames?.[`${linkedStream.sourceId}:${linkedStream.categoryId}`] || `Category ${linkedStream.categoryId}`
                                : '—'}
                            </TableCell>
                            <TableCell>
                              <Button
                                size="small"
                                variant="outlined"
                                onClick={() => {
                                  sessionStorage.setItem('streamDetailReferrer', window.location.pathname);
                                  navigate(`/streams/${linkedStream.id}/${streamType}`);
                                }}
                              >
                                View
                              </Button>
                            </TableCell>
                          </TableRow>
                        );
                      })}
                    </TableBody>
                  </Table>
                </TableContainer>
              </Box>
            ) : (
              <Typography variant="body2" color="text.secondary">
                No other streams with the same TMDB ID found
              </Typography>
            )}
          </TabPanel>
        )}

        {/* Raw Data Tab */}
        {hasRawData && (
          <TabPanel value={tabValue} index={rawDataTabIndex}>
            <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
              <Typography variant="subtitle2" sx={{ fontWeight: 600 }}>
                JSON
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
          </TabPanel>
        )}
      </Card>

      {/* Removed: Raw Data JSON section (now in tabs) */}

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
