import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import {
  Box,
  Paper,
  Typography,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  CircularProgress,
  Alert,
  IconButton,
  Chip,
} from '@mui/material';
import { Refresh as RefreshIcon } from '@mui/icons-material';
import { cacheApi, type CacheStat } from '../services/cacheApi';

export default function CacheStats() {
  const [lastRefresh, setLastRefresh] = useState<Date>(new Date());

  const { data: cacheStats, isLoading, error, refetch } = useQuery({
    queryKey: ['cacheStats', lastRefresh],
    queryFn: () => cacheApi.getCacheStats(),
  });

  const handleRefresh = () => {
    setLastRefresh(new Date());
    refetch();
  };

  const formatNumber = (num: number): string => {
    return num.toLocaleString();
  };

  const formatPercentage = (num: number): string => {
    return `${(num * 100).toFixed(2)}%`;
  };

  const getHitRateColor = (hitRate: number): 'success' | 'warning' | 'error' => {
    if (hitRate >= 0.8) return 'success';
    if (hitRate >= 0.5) return 'warning';
    return 'error';
  };

  return (
    <Box sx={{ p: 3 }}>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 3 }}>
        <Typography variant="h4">Cache Statistics</Typography>
        <IconButton onClick={handleRefresh} color="primary" disabled={isLoading}>
          <RefreshIcon />
        </IconButton>
      </Box>

      {error && (
        <Alert severity="error" sx={{ mb: 2 }}>
          Failed to load cache statistics: {error instanceof Error ? error.message : 'Unknown error'}
        </Alert>
      )}

      {isLoading ? (
        <Box sx={{ display: 'flex', justifyContent: 'center', p: 4 }}>
          <CircularProgress />
        </Box>
      ) : cacheStats && cacheStats.length > 0 ? (
        <TableContainer component={Paper}>
          <Table>
            <TableHead>
              <TableRow>
                <TableCell><strong>Cache Name</strong></TableCell>
                <TableCell align="right"><strong>Size</strong></TableCell>
                <TableCell align="right"><strong>Max Size</strong></TableCell>
                <TableCell align="right"><strong>Hits</strong></TableCell>
                <TableCell align="right"><strong>Misses</strong></TableCell>
                <TableCell align="right"><strong>Hit Rate</strong></TableCell>
                <TableCell align="right"><strong>Puts</strong></TableCell>
                <TableCell align="right"><strong>Size Evictions</strong></TableCell>
                <TableCell align="right"><strong>Expired Evictions</strong></TableCell>
                <TableCell align="right"><strong>Total Evictions</strong></TableCell>
                <TableCell align="right"><strong>Invalidations</strong></TableCell>
                <TableCell align="right"><strong>Clears</strong></TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {cacheStats.map((stat: CacheStat) => {
                const hitRate = stat.hits + stat.misses > 0 
                  ? stat.hits / (stat.hits + stat.misses) 
                  : 0;
                const totalEvictions = stat.sizeEvictions + stat.expiredEvictions;

                return (
                  <TableRow key={stat.cacheName} hover>
                    <TableCell component="th" scope="row">
                      <Typography variant="body2" fontWeight="medium">
                        {stat.cacheName}
                      </Typography>
                    </TableCell>
                    <TableCell align="right">{formatNumber(stat.size)}</TableCell>
                    <TableCell align="right">
                      {stat.maxSize === 0 ? '∞' : formatNumber(stat.maxSize)}
                    </TableCell>
                    <TableCell align="right">{formatNumber(stat.hits)}</TableCell>
                    <TableCell align="right">{formatNumber(stat.misses)}</TableCell>
                    <TableCell align="right">
                      <Chip 
                        label={formatPercentage(hitRate)}
                        color={getHitRateColor(hitRate)}
                        size="small"
                      />
                    </TableCell>
                    <TableCell align="right">{formatNumber(stat.puts)}</TableCell>
                    <TableCell align="right">{formatNumber(stat.sizeEvictions)}</TableCell>
                    <TableCell align="right">{formatNumber(stat.expiredEvictions)}</TableCell>
                    <TableCell align="right">{formatNumber(totalEvictions)}</TableCell>
                    <TableCell align="right">{formatNumber(stat.invalidations)}</TableCell>
                    <TableCell align="right">{formatNumber(stat.clears)}</TableCell>
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
        </TableContainer>
      ) : (
        <Paper sx={{ p: 4, textAlign: 'center' }}>
          <Typography variant="body1" color="text.secondary">
            No cache statistics available
          </Typography>
        </Paper>
      )}
    </Box>
  );
}
