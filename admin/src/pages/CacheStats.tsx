import { useState, useMemo } from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import {
  Button,
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
  TableSortLabel,
} from '@mui/material';
import { Refresh as RefreshIcon } from '@mui/icons-material';
import {
  cacheApi,
  type CacheStat,
  type DatabaseShrinkResult,
} from '../services/cacheApi';

type OrderBy = 'cacheName' | 'size' | 'maxSize' | 'hits' | 'misses' | 'hitRate' | 'puts' | 'sizeEvictions' | 'expiredEvictions' | 'totalEvictions' | 'invalidations' | 'clears';
type Order = 'asc' | 'desc';

export default function CacheStats() {
  const [lastRefresh, setLastRefresh] = useState<Date>(new Date());
  const [orderBy, setOrderBy] = useState<OrderBy>('cacheName');
  const [order, setOrder] = useState<Order>('asc');
  const [shrinkResult, setShrinkResult] = useState<DatabaseShrinkResult | null>(null);
  const [shrinkErrorMessage, setShrinkErrorMessage] = useState<string | null>(null);

  const { data: cacheStats, isLoading, error, refetch } = useQuery({
    queryKey: ['cacheStats', lastRefresh],
    queryFn: () => cacheApi.getCacheStats(),
  });

  const handleRefresh = () => {
    setLastRefresh(new Date());
    refetch();
  };

  const shrinkMutation = useMutation({
    mutationFn: () => cacheApi.shrinkDatabase(),
    onSuccess: (result) => {
      setShrinkResult(result);
      setShrinkErrorMessage(null);
      handleRefresh();
    },
    onError: (mutationError) => {
      const message =
        mutationError instanceof Error ? mutationError.message : 'Unknown error while shrinking database';
      setShrinkResult(null);
      setShrinkErrorMessage(message);
    },
  });

  const handleShrinkDatabase = () => {
    setShrinkErrorMessage(null);
    shrinkMutation.mutate();
  };

  const handleSort = (column: OrderBy) => {
    const isAsc = orderBy === column && order === 'asc';
    setOrder(isAsc ? 'desc' : 'asc');
    setOrderBy(column);
  };

  const sortedCacheStats = useMemo(() => {
    if (!cacheStats) return [];
    
    return [...cacheStats].sort((a, b) => {
      let aVal: number | string;
      let bVal: number | string;

      if (orderBy === 'hitRate') {
        aVal = a.hits + a.misses > 0 ? a.hits / (a.hits + a.misses) : 0;
        bVal = b.hits + b.misses > 0 ? b.hits / (b.hits + b.misses) : 0;
      } else if (orderBy === 'totalEvictions') {
        aVal = a.sizeEvictions + a.expiredEvictions;
        bVal = b.sizeEvictions + b.expiredEvictions;
      } else if (orderBy === 'cacheName') {
        aVal = a.cacheName;
        bVal = b.cacheName;
      } else {
        aVal = a[orderBy];
        bVal = b[orderBy];
      }

      if (aVal < bVal) return order === 'asc' ? -1 : 1;
      if (aVal > bVal) return order === 'asc' ? 1 : -1;
      return 0;
    });
  }, [cacheStats, orderBy, order]);

  const formatNumber = (num: number): string => {
    return num.toLocaleString();
  };

  const formatPercentage = (num: number): string => {
    return `${(num * 100).toFixed(2)}%`;
  };

  const formatBytes = (bytes: number): string => {
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(2)} KB`;
    if (bytes < 1024 * 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(2)} MB`;
    return `${(bytes / (1024 * 1024 * 1024)).toFixed(2)} GB`;
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
        <Box sx={{ display: 'flex', gap: 1 }}>
          <Button
            variant="contained"
            onClick={handleShrinkDatabase}
            disabled={isLoading || shrinkMutation.isPending}
          >
            {shrinkMutation.isPending ? 'Shrinking...' : 'Shrink Database'}
          </Button>
          <IconButton
            onClick={handleRefresh}
            color="primary"
            disabled={isLoading || shrinkMutation.isPending}
          >
            <RefreshIcon />
          </IconButton>
        </Box>
      </Box>

      {shrinkResult && (
        <Alert severity="success" sx={{ mb: 2 }}>
          Database shrunk successfully. Freed {formatBytes(shrinkResult.freedBytes)} in{' '}
          {shrinkResult.durationMs} ms ({formatBytes(shrinkResult.sizeBeforeBytes)} -&gt;{' '}
          {formatBytes(shrinkResult.sizeAfterBytes)}).
        </Alert>
      )}

      {shrinkErrorMessage && (
        <Alert severity="error" sx={{ mb: 2 }}>
          Failed to shrink database: {shrinkErrorMessage}
        </Alert>
      )}

      {error && (
        <Alert severity="error" sx={{ mb: 2 }}>
          Failed to load cache statistics: {error instanceof Error ? error.message : 'Unknown error'}
        </Alert>
      )}

      {isLoading ? (
        <Box sx={{ display: 'flex', justifyContent: 'center', p: 4 }}>
          <CircularProgress />
        </Box>
      ) : sortedCacheStats && sortedCacheStats.length > 0 ? (
        <TableContainer component={Paper}>
          <Table>
            <TableHead>
              <TableRow>
                <TableCell>
                  <TableSortLabel
                    active={orderBy === 'cacheName'}
                    direction={orderBy === 'cacheName' ? order : 'asc'}
                    onClick={() => handleSort('cacheName')}
                  >
                    <strong>Cache Name</strong>
                  </TableSortLabel>
                </TableCell>
                <TableCell align="right">
                  <TableSortLabel
                    active={orderBy === 'size'}
                    direction={orderBy === 'size' ? order : 'asc'}
                    onClick={() => handleSort('size')}
                  >
                    <strong>Size</strong>
                  </TableSortLabel>
                </TableCell>
                <TableCell align="right">
                  <TableSortLabel
                    active={orderBy === 'maxSize'}
                    direction={orderBy === 'maxSize' ? order : 'asc'}
                    onClick={() => handleSort('maxSize')}
                  >
                    <strong>Max Size</strong>
                  </TableSortLabel>
                </TableCell>
                <TableCell align="right">
                  <TableSortLabel
                    active={orderBy === 'hits'}
                    direction={orderBy === 'hits' ? order : 'asc'}
                    onClick={() => handleSort('hits')}
                  >
                    <strong>Hits</strong>
                  </TableSortLabel>
                </TableCell>
                <TableCell align="right">
                  <TableSortLabel
                    active={orderBy === 'misses'}
                    direction={orderBy === 'misses' ? order : 'asc'}
                    onClick={() => handleSort('misses')}
                  >
                    <strong>Misses</strong>
                  </TableSortLabel>
                </TableCell>
                <TableCell align="right">
                  <TableSortLabel
                    active={orderBy === 'hitRate'}
                    direction={orderBy === 'hitRate' ? order : 'asc'}
                    onClick={() => handleSort('hitRate')}
                  >
                    <strong>Hit Rate</strong>
                  </TableSortLabel>
                </TableCell>
                <TableCell align="right">
                  <TableSortLabel
                    active={orderBy === 'puts'}
                    direction={orderBy === 'puts' ? order : 'asc'}
                    onClick={() => handleSort('puts')}
                  >
                    <strong>Puts</strong>
                  </TableSortLabel>
                </TableCell>
                <TableCell align="right">
                  <TableSortLabel
                    active={orderBy === 'sizeEvictions'}
                    direction={orderBy === 'sizeEvictions' ? order : 'asc'}
                    onClick={() => handleSort('sizeEvictions')}
                  >
                    <strong>Size Evictions</strong>
                  </TableSortLabel>
                </TableCell>
                <TableCell align="right">
                  <TableSortLabel
                    active={orderBy === 'expiredEvictions'}
                    direction={orderBy === 'expiredEvictions' ? order : 'asc'}
                    onClick={() => handleSort('expiredEvictions')}
                  >
                    <strong>Expired Evictions</strong>
                  </TableSortLabel>
                </TableCell>
                <TableCell align="right">
                  <TableSortLabel
                    active={orderBy === 'totalEvictions'}
                    direction={orderBy === 'totalEvictions' ? order : 'asc'}
                    onClick={() => handleSort('totalEvictions')}
                  >
                    <strong>Total Evictions</strong>
                  </TableSortLabel>
                </TableCell>
                <TableCell align="right">
                  <TableSortLabel
                    active={orderBy === 'invalidations'}
                    direction={orderBy === 'invalidations' ? order : 'asc'}
                    onClick={() => handleSort('invalidations')}
                  >
                    <strong>Invalidations</strong>
                  </TableSortLabel>
                </TableCell>
                <TableCell align="right">
                  <TableSortLabel
                    active={orderBy === 'clears'}
                    direction={orderBy === 'clears' ? order : 'asc'}
                    onClick={() => handleSort('clears')}
                  >
                    <strong>Clears</strong>
                  </TableSortLabel>
                </TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {sortedCacheStats.map((stat: CacheStat) => {
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
                      {formatNumber(stat.maxSize)}
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
