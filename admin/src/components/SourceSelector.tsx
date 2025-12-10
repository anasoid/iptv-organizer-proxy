import { useEffect, useState } from 'react';
import { Box, FormControl, InputLabel, Select, MenuItem, CircularProgress, FormHelperText } from '@mui/material';
import { useQuery } from '@tanstack/react-query';
import sourcesApi, { type Source } from '../services/sourcesApi';
import { useAuthStore } from '../stores/authStore';

interface SourceSelectorProps {
  sourceId: number | null;
  onChange: (sourceId: number) => void;
  required?: boolean;
}

export default function SourceSelector({
  sourceId,
  onChange,
  required = true,
}: SourceSelectorProps) {
  const { isAuthenticated } = useAuthStore();
  const [sources, setSources] = useState<Source[]>([]);
  const [isLoading, setIsLoading] = useState(false);

  // Fetch all sources (no pagination for dropdown)
  const { data, isLoading: isFetching } = useQuery({
    queryKey: ['sources-all'],
    queryFn: async () => {
      const result = await sourcesApi.getSources(1, 100);
      return result.data;
    },
    enabled: isAuthenticated,
    staleTime: 5 * 60 * 1000, // 5 minutes
  });

  useEffect(() => {
    if (data) {
      setSources(data);
      setIsLoading(false);
    } else if (isFetching) {
      setIsLoading(true);
    }
  }, [data, isFetching]);

  return (
    <FormControl fullWidth required={required} size="small">
      <InputLabel>Source</InputLabel>
      <Select
        value={sourceId ?? ''}
        onChange={(e) => onChange(Number(e.target.value))}
        label="Source"
        disabled={isLoading || isFetching}
        startAdornment={isLoading ? <CircularProgress size={20} style={{ marginRight: 8 }} /> : undefined}
      >
        <MenuItem value="">
          <em>Select a source...</em>
        </MenuItem>
        {sources.map((source) => (
          <MenuItem key={source.id} value={source.id}>
            {source.name}
          </MenuItem>
        ))}
      </Select>
      {required && !sourceId && <FormHelperText error>Source is required</FormHelperText>}
    </FormControl>
  );
}
