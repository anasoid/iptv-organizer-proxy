import { useState } from 'react';
import {
  Box,
  Button,
  TextField,
  DialogTitle,
  DialogContent,
  DialogActions,
  Alert,
  Stack,
} from '@mui/material';
import Editor from '@monaco-editor/react';
import { useMutation } from '@tanstack/react-query';
import type { Filter } from '../services/filtersApi';
import filtersApi from '../services/filtersApi';

interface FilterFormProps {
  filter: Filter | null;
  onSuccess: () => void;
  onCancel: () => void;
}

const FILTER_TEMPLATES = {
  blockAdult: {
    name: 'Block Adult Content',
    config: `rules:
  include: []
  exclude:
    - pattern: "adult|xxx|18\\+"
      case_sensitive: false
    - pattern: "porn|xxx"
      case_sensitive: false
favoris: []
`,
  },
  sportsOnly: {
    name: 'Sports Only',
    config: `rules:
  include:
    - pattern: "sports|soccer|football|basketball|nba|nfl|premier"
      case_sensitive: false
    - pattern: "espn|bein|dazn"
      case_sensitive: false
  exclude: []
favoris: []
`,
  },
  hdChannels: {
    name: 'HD Channels Only',
    config: `rules:
  include:
    - pattern: "hd|1080|4k|ultra"
      case_sensitive: false
  exclude:
    - pattern: "sd|480p"
      case_sensitive: false
favoris: []
`,
  },
  kidsChannels: {
    name: 'Kids Channels',
    config: `rules:
  include:
    - pattern: "kids|cartoon|disney|nickelodeon|boomerang"
      case_sensitive: false
  exclude: []
favoris: []
`,
  },
};

export default function FilterForm({ filter, onSuccess, onCancel }: FilterFormProps) {
  const [name, setName] = useState(filter?.name || '');
  const [description, setDescription] = useState(filter?.description || '');
  const [yamlConfig, setYamlConfig] = useState(filter?.filter_config || '');
  const [error, setError] = useState<string | null>(null);

  const createMutation = useMutation({
    mutationFn: (data: Parameters<typeof filtersApi.createFilter>[0]) =>
      filtersApi.createFilter(data),
    onSuccess: () => {
      onSuccess();
    },
    onError: (err) => {
      setError(err instanceof Error ? err.message : 'Failed to create filter');
    },
  });

  const updateMutation = useMutation({
    mutationFn: (data: Parameters<typeof filtersApi.updateFilter>[1]) =>
      filtersApi.updateFilter(filter!.id, data),
    onSuccess: () => {
      onSuccess();
    },
    onError: (err) => {
      setError(err instanceof Error ? err.message : 'Failed to update filter');
    },
  });

  const validateYAML = () => {
    try {
      // Basic YAML validation - check for required sections
      if (!yamlConfig.includes('rules:') && !yamlConfig.includes('favoris:')) {
        setError('YAML must contain "rules:" or "favoris:" sections');
        return false;
      }
      setError(null);
      return true;
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Invalid YAML');
      return false;
    }
  };

  const handleApplyTemplate = (templateKey: keyof typeof FILTER_TEMPLATES) => {
    const template = FILTER_TEMPLATES[templateKey];
    setName(filter?.name || template.name);
    setYamlConfig(template.config);
    setError(null);
  };

  const handleSubmit = async () => {
    if (!name.trim()) {
      setError('Filter name is required');
      return;
    }

    if (!validateYAML()) {
      return;
    }

    const data = {
      name: name.trim(),
      description: description.trim() || undefined,
      filter_config: yamlConfig,
    };

    if (filter) {
      updateMutation.mutate(data);
    } else {
      createMutation.mutate(data);
    }
  };

  const isLoading = createMutation.isPending || updateMutation.isPending;

  return (
    <>
      <DialogTitle>{filter ? 'Edit Filter' : 'Create New Filter'}</DialogTitle>
      <DialogContent sx={{ minWidth: 800, pt: 2 }}>
        {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}

        {/* Basic Info */}
        <TextField
          fullWidth
          label="Filter Name"
          value={name}
          onChange={(e) => setName(e.target.value)}
          margin="normal"
          required
        />

        <TextField
          fullWidth
          label="Description"
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          margin="normal"
          multiline
          rows={2}
        />

        {/* Template Buttons */}
        {!filter && (
          <Box sx={{ mt: 3, mb: 3 }}>
            <p style={{ margin: '8px 0', fontSize: '0.875rem', color: '#666' }}>
              Quick Templates:
            </p>
            <Stack direction="row" spacing={1} sx={{ flexWrap: 'wrap', gap: 1 }}>
              {Object.entries(FILTER_TEMPLATES).map(([key, template]) => (
                <Button
                  key={key}
                  variant="outlined"
                  size="small"
                  onClick={() => handleApplyTemplate(key as keyof typeof FILTER_TEMPLATES)}
                >
                  {template.name}
                </Button>
              ))}
            </Stack>
          </Box>
        )}

        {/* YAML Editor */}
        <Box sx={{ mt: 3, mb: 2 }}>
          <p style={{ margin: '8px 0', fontSize: '0.875rem', fontWeight: 500 }}>
            Filter Configuration (YAML)
          </p>
          <Box
            sx={{
              border: '1px solid #ccc',
              borderRadius: 1,
              overflow: 'hidden',
              height: 400,
              backgroundColor: '#f5f5f5',
            }}
          >
            <Editor
              height="100%"
              defaultLanguage="yaml"
              value={yamlConfig}
              onChange={(value) => {
                setYamlConfig(value || '');
                setError(null);
              }}
              theme="vs"
              options={{
                minimap: { enabled: false },
                wordWrap: 'on',
                lineNumbers: 'on',
                scrollBeyondLastLine: false,
                automaticLayout: true,
              }}
            />
          </Box>
          <p style={{ margin: '8px 0', fontSize: '0.75rem', color: '#999' }}>
            Define filter rules with include/exclude patterns and favorite categories (100000+)
          </p>
        </Box>

        {/* Validate Button */}
        <Box sx={{ mb: 2 }}>
          <Button
            variant="outlined"
            size="small"
            onClick={validateYAML}
          >
            Validate YAML
          </Button>
        </Box>
      </DialogContent>
      <DialogActions>
        <Button onClick={onCancel} disabled={isLoading}>
          Cancel
        </Button>
        <Button
          onClick={handleSubmit}
          variant="contained"
          disabled={isLoading || !name.trim()}
        >
          {isLoading ? 'Saving...' : filter ? 'Update Filter' : 'Create Filter'}
        </Button>
      </DialogActions>
    </>
  );
}
