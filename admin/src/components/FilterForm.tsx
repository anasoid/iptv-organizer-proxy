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
    rules: `include: []
exclude:
  - pattern: "adult|xxx|18\\+"
    case_sensitive: false
  - pattern: "porn|xxx"
    case_sensitive: false
`,
    favoris: `# Virtual categories for organizing content
# Each favoris gets a unique ID starting at 100000
# - name: "Kids Favorites"
#   target_group: "Kids Corner"
#   match:
#     channels:
#       by_name: ["Disney", "Cartoon"]
`,
  },
  sportsOnly: {
    name: 'Sports Only',
    rules: `include:
  - pattern: "sports|soccer|football|basketball|nba|nfl|premier"
    case_sensitive: false
  - pattern: "espn|bein|dazn"
    case_sensitive: false
exclude: []
`,
    favoris: `# - name: "My Favorites"
#   target_group: "Favorites"
#   match:
#     channels:
#       by_name: ["ESPN", "Sports"]
`,
  },
  hdChannels: {
    name: 'HD Channels Only',
    rules: `include:
  - pattern: "hd|1080|4k|ultra"
    case_sensitive: false
exclude:
  - pattern: "sd|480p"
    case_sensitive: false
`,
    favoris: `# - name: "Premium"
#   target_group: "Premium HD"
#   match:
#     channels:
#       by_name: ["4K"]
`,
  },
  kidsChannels: {
    name: 'Kids Channels',
    rules: `include:
  - pattern: "kids|cartoon|disney|nickelodeon|boomerang"
    case_sensitive: false
exclude: []
`,
    favoris: `- name: "Kids Favorites"
  target_group: "Kids Corner"
  match:
    channels:
      by_name: ["Disney", "Cartoon", "Nickelodeon"]
`,
  },
};

export default function FilterForm({ filter, onSuccess, onCancel }: FilterFormProps) {
  const [name, setName] = useState(filter?.name || '');
  const [description, setDescription] = useState(filter?.description || '');
  const [rulesYaml, setRulesYaml] = useState(filter?.filter_config || '');
  const [favorisYaml, setFavorisYaml] = useState(filter?.favoris || '');
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
      // Rules section is required
      if (!rulesYaml.trim()) {
        setError('Rules configuration is required');
        return false;
      }
      // Check for basic YAML structure in rules
      if (!rulesYaml.includes('include:') && !rulesYaml.includes('exclude:')) {
        setError('Rules must contain "include:" or "exclude:" sections');
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
    setRulesYaml(template.rules);
    setFavorisYaml(template.favoris);
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
      filter_config: rulesYaml,
      ...(favorisYaml.trim() && { favoris: favorisYaml }),
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

        {/* Rules YAML Editor */}
        <Box sx={{ mt: 3, mb: 2 }}>
          <p style={{ margin: '8px 0', fontSize: '0.875rem', fontWeight: 500 }}>
            Filter Rules (YAML) *
          </p>
          <Box
            sx={{
              border: '1px solid #ccc',
              borderRadius: 1,
              overflow: 'hidden',
              height: 300,
              backgroundColor: '#f5f5f5',
            }}
          >
            <Editor
              height="100%"
              defaultLanguage="yaml"
              value={rulesYaml}
              onChange={(value) => {
                setRulesYaml(value || '');
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
            Define include/exclude patterns to filter streams and categories
          </p>
        </Box>

        {/* Favoris YAML Editor */}
        <Box sx={{ mt: 3, mb: 2 }}>
          <p style={{ margin: '8px 0', fontSize: '0.875rem', fontWeight: 500 }}>
            Virtual Categories / Favoris (YAML)
          </p>
          <Box
            sx={{
              border: '1px solid #ccc',
              borderRadius: 1,
              overflow: 'hidden',
              height: 300,
              backgroundColor: '#f5f5f5',
            }}
          >
            <Editor
              height="100%"
              defaultLanguage="yaml"
              value={favorisYaml}
              onChange={(value) => {
                setFavorisYaml(value || '');
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
            Define virtual categories using target_group with match criteria (generates category IDs starting at 100000)
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
