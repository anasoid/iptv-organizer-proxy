import { Card, CardMedia, CardContent, Typography, Box, Chip } from '@mui/material';
import type { Stream } from '../services/streamsApi';

interface StreamCardProps {
  stream: Stream;
  categoryName?: string;
  onClick?: () => void;
}

export default function StreamCard({ stream, categoryName, onClick }: StreamCardProps) {
  // Extract stream_icon from data field
  const streamIcon = stream.data?.stream_icon || '';

  // Try to get duration or other info from data
  const getDuration = (): string | null => {
    if (!stream.data) return null;
    if (stream.data.duration) {
      return typeof stream.data.duration === 'number'
        ? formatSeconds(stream.data.duration)
        : stream.data.duration;
    }
    return null;
  };

  const formatSeconds = (seconds: number): string => {
    const hours = Math.floor(seconds / 3600);
    const minutes = Math.floor((seconds % 3600) / 60);
    const secs = seconds % 60;

    if (hours > 0) {
      return `${hours}h ${minutes}m`;
    }
    return `${minutes}m ${secs}s`;
  };

  const getTypeColor = (type?: string): 'default' | 'primary' | 'secondary' | 'error' | 'warning' | 'info' | 'success' => {
    // Try to determine type from stream name labels
    if (stream.labels) {
      const labels = stream.labels.toLowerCase();
      if (labels.includes('live')) return 'primary';
      if (labels.includes('movie') || labels.includes('vod')) return 'secondary';
      if (labels.includes('series')) return 'warning';
    }
    return 'default';
  };

  const duration = getDuration();

  return (
    <Card
      onClick={onClick}
      sx={{
        height: '100%',
        display: 'flex',
        flexDirection: 'column',
        cursor: 'pointer',
        transition: 'transform 0.2s, box-shadow 0.2s',
        '&:hover': {
          transform: 'translateY(-4px)',
          boxShadow: (theme) => theme.shadows[8],
        },
      }}
    >
      {/* Stream Icon/Image */}
      {streamIcon ? (
        <CardMedia
          component="img"
          height="200"
          image={streamIcon}
          alt={stream.name}
          sx={{
            objectFit: 'cover',
            backgroundColor: '#f0f0f0',
          }}
          onError={(e) => {
            const target = e.target as HTMLImageElement;
            target.style.display = 'none';
          }}
        />
      ) : (
        <Box
          sx={{
            height: 200,
            backgroundColor: '#f0f0f0',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            color: '#999',
          }}
        >
          <Typography variant="body2">No Image</Typography>
        </Box>
      )}

      <CardContent sx={{ flexGrow: 1, pb: 1 }}>
        {/* Stream Name */}
        <Typography
          variant="subtitle2"
          component="div"
          sx={{
            fontWeight: 600,
            mb: 0.5,
            overflow: 'hidden',
            textOverflow: 'ellipsis',
            display: '-webkit-box',
            WebkitLineClamp: 2,
            WebkitBoxOrient: 'vertical',
            minHeight: '2.4em',
          }}
        >
          {stream.name}
        </Typography>

        {/* Category */}
        {categoryName && (
          <Typography
            variant="caption"
            sx={{
              color: 'text.secondary',
              display: 'block',
              mb: 0.5,
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              whiteSpace: 'nowrap',
            }}
          >
            {categoryName}
          </Typography>
        )}

        {/* Chips for type and duration */}
        <Box sx={{ display: 'flex', gap: 0.5, flexWrap: 'wrap', mt: 1 }}>
          <Chip
            label={stream.labels?.split(',')[0]?.trim() || 'Stream'}
            size="small"
            color={getTypeColor()}
            variant="outlined"
          />
          {duration && <Chip label={duration} size="small" variant="outlined" />}
          {stream.is_adult ? <Chip label="Adult" size="small" color="error" variant="outlined" /> : null}
        </Box>
      </CardContent>
    </Card>
  );
}
