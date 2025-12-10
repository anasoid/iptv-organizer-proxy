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
  TableHead,
  TableRow,
  Paper,
  IconButton,
  Tooltip,
  Snackbar,
} from '@mui/material';
import { ArrowBack as ArrowBackIcon, ContentCopy as ContentCopyIcon } from '@mui/icons-material';
import { useQuery } from '@tanstack/react-query';
import { useAuthStore } from '../stores/authStore';
import streamsApi from '../services/streamsApi';
import categoriesApi from '../services/categoriesApi';

export default function StreamDetail() {
  const { id, type } = useParams<{ id: string; type: string }>();
  const navigate = useNavigate();
  const { isAuthenticated } = useAuthStore();
  const [snackbarOpen, setSnackbarOpen] = useState(false);
  const [snackbarMessage, setSnackbarMessage] = useState('');

  const streamId = id ? parseInt(id, 10) : null;
  const streamType = (type || 'live') as 'live' | 'vod' | 'series';

  // Fetch stream details
  const { data: streamData, isLoading: isLoadingStream, error: streamError } = useQuery({
    queryKey: ['stream', streamId],
    queryFn: () => (streamId ? streamsApi.getStream(streamId) : Promise.resolve(null)),
    enabled: isAuthenticated && streamId !== null,
  });

  const stream = streamData?.data;

  // Fetch source ID from stream to get all categories as fallback
  const sourceId = stream?.source_id;

  // First try to get single category, fallback to fetching all categories
  const { data: categoryData } = useQuery({
    queryKey: ['category', stream?.category_id],
    queryFn: async () => {
      if (!stream?.category_id) {
        console.log('No category_id on stream');
        return Promise.resolve(null);
      }
      console.log('Fetching category:', { category_id: stream.category_id, source_id: sourceId });
      try {
        const result = await categoriesApi.getCategory(Number(stream.category_id), sourceId);
        console.log('Category found:', result.data.category_name);
        return result;
      } catch {
        console.log('Single category fetch failed, trying to fetch all categories from source...');
        // Fallback: fetch all categories and find the matching one
        if (sourceId) {
          try {
            const allCats = await categoriesApi.getCategories(sourceId, 1, 100);
            const found = allCats.data.find((cat) => cat.id === Number(stream.category_id));
            if (found) {
              console.log('Category found in source categories:', found.category_name);
              return { success: true, data: found };
            } else {
              console.log('Category not found in source categories either');
              return null;
            }
          } catch (fallbackErr) {
            console.log('Fallback category fetch error:', fallbackErr);
            return null;
          }
        }
        return null;
      }
    },
    enabled: isAuthenticated && !!stream?.category_id,
  });

  const category = categoryData?.data;

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

  const streamIcon = stream.data?.stream_icon;
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
          {/* Image Section */}
          {streamIcon && (
            <Grid item xs={12} sm={4} md={3}>
              <CardMedia
                component="img"
                image={streamIcon}
                alt={stream.name}
                sx={{
                  height: '100%',
                  minHeight: 300,
                  objectFit: 'cover',
                  backgroundColor: '#f0f0f0',
                }}
              />
            </Grid>
          )}

          {/* Info Section */}
          <Grid item xs={12} sm={streamIcon ? 8 : 12} md={streamIcon ? 9 : 12}>
            <Box sx={{ p: 3 }}>
              {/* Header with Type Badge and Category */}
              <Box sx={{ display: 'flex', gap: 1, alignItems: 'center', mb: 2, flexWrap: 'wrap' }}>
                <Typography variant="h4" sx={{ flex: '1 1 auto' }}>
                  {stream.name}
                </Typography>
                {stream.category_id && (
                  <Chip
                    label={category?.category_name || `Category ${stream.category_id}`}
                    variant="outlined"
                    color={category ? 'default' : 'warning'}
                    size="medium"
                    onClick={() => {
                      // Store the selected category and navigate to stream listing
                      sessionStorage.setItem('streamDetailReferrer', window.location.pathname);
                      sessionStorage.setItem('filterByCategoryId', String(stream.category_id));
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
                  <Typography variant="body1">{stream.stream_id}</Typography>
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
                      {category.category_name}
                    </Typography>
                  </Grid>
                )}

                {stream.is_adult && (
                  <Grid item xs={12} sm={6}>
                    <Chip label="Adult Content" color="error" />
                  </Grid>
                )}

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
          </Grid>
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

      {/* Additional Data */}
      {stream.data && Object.keys(stream.data).length > 0 && (
        <Card sx={{ p: 3 }}>
          <Typography variant="h6" sx={{ mb: 2, fontWeight: 600 }}>
            Metadata
          </Typography>

          <TableContainer>
            <Table size="small">
              <TableHead>
                <TableRow sx={{ backgroundColor: 'action.hover' }}>
                  <TableCell sx={{ fontWeight: 600 }}>Key</TableCell>
                  <TableCell sx={{ fontWeight: 600 }}>Value</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {Object.entries(stream.data).map(([key, value]) => {
                  // Skip certain fields we already display
                  if (['stream_icon', 'url', 'duration', 'episodes', 'seasons'].includes(key)) {
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
                      <TableCell sx={{ verticalAlign: 'top', fontWeight: 500 }}>{key}</TableCell>
                      <TableCell>
                        <Typography
                          variant="body2"
                          sx={{
                            fontFamily: 'monospace',
                            fontSize: '0.875rem',
                            wordBreak: 'break-word',
                            maxWidth: 600,
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
