import { useState, useEffect } from 'react';
import {
  Box,
  Button,
  TextField,
  DialogTitle,
  DialogContent,
  DialogActions,
  Alert,
  Stack,
  Dialog,
  IconButton,
  Tabs,
  Tab,
  Typography,
} from '@mui/material';
import HelpOutlineIcon from '@mui/icons-material/HelpOutline';
import Editor from '@monaco-editor/react';
import { useMutation } from '@tanstack/react-query';
import type { AxiosError } from 'axios';
import type { Filter } from '../services/filtersApi';
import filtersApi from '../services/filtersApi';

/**
 * Extract error message from axios error response
 */
function getErrorMessage(err: unknown): string {
  if (err instanceof Error) {
    // Check if it's an axios error with response data
    const axiosErr = err as AxiosError<{ message?: string; success?: boolean }>;
    if (axiosErr.response?.data?.message) {
      return axiosErr.response.data.message;
    }
    return err.message;
  }
  return 'An unexpected error occurred';
}

interface FilterFormProps {
  filter: Filter | null;
  onSuccess: () => void;
  onCancel: () => void;
}

const RULES_CONFIG_EXAMPLES = {
  blockAdult: {
    title: 'Block Adult Content',
    description: 'Exclude streams and categories with adult content',
    yaml: `rules:
  - name: "Block Adult Content"
    type: exclude
    match:
      categories:
        by_name: ["*Adult*", "*XXX*", "*18+*"]
        by_labels: ["adult", "18+"]
      channels:
        by_name: ["Playboy*", "*Adult*"]
        by_labels: ["adult", "xxx"]`,
  },
  sportsOnly: {
    title: 'Sports Only',
    description: 'Include only sports-related content',
    yaml: `rules:
  - name: "Include Sports"
    type: include
    match:
      categories:
        by_name: ["*Sports*", "*Football*", "*Basketball*"]
        by_labels: ["sports"]
      channels:
        by_labels: ["sports", "live"]`,
  },
  hdOnly: {
    title: 'HD Channels Only',
    description: 'Include HD/4K channels and exclude SD',
    yaml: `rules:
  - name: "Include HD"
    type: include
    match:
      channels:
        by_name: ["*HD*", "*1080*", "*4K*", "*Ultra*"]
        by_labels: ["hd"]

  - name: "Exclude SD"
    type: exclude
    match:
      channels:
        by_name: ["*SD*", "*480*"]
        by_labels: ["sd"]`,
  },
  internationalOnly: {
    title: 'International Channels',
    description: 'Include only international channels with language/quality metadata',
    yaml: `rules:
  - name: "Include International HD"
    type: include
    match:
      channels:
        by_name: ["FR*", "DE*", "ES*", "IT*", "*BBC*", "*ITV*"]
        by_labels: ["international", "hd"]`,
  },
};

const FILTER_TEMPLATES = {
  blockAdult: {
    name: 'Block Adult Content',
    rules: `rules:
  - name: "Block Adult Content"
    type: exclude
    match:
      categories:
        by_name: ["*Adult*", "*XXX*", "*18+*"]
        by_labels: ["adult", "18+"]
      channels:
        by_name: ["Playboy*", "*Adult*"]
        by_labels: ["adult", "xxx"]
`,
  },
  sportsOnly: {
    name: 'Sports Only',
    rules: `rules:
  - name: "Include Sports"
    type: include
    match:
      categories:
        by_name: ["*Sports*", "*Football*", "*Basketball*"]
        by_labels: ["sports"]
      channels:
        by_labels: ["sports", "live"]
`,
  },
  hdChannels: {
    name: 'HD Channels Only',
    rules: `rules:
  - name: "Include HD"
    type: include
    match:
      channels:
        by_name: ["*HD*", "*1080*", "*4K*", "*Ultra*"]
        by_labels: ["hd"]

  - name: "Exclude SD"
    type: exclude
    match:
      channels:
        by_name: ["*SD*", "*480*"]
        by_labels: ["sd"]
`,
  },
  kidsChannels: {
    name: 'Kids Channels',
    rules: `rules:
  - name: "Include Kids Content"
    type: include
    match:
      categories:
        by_name: ["*Kids*", "*Children*", "*Family*"]
        by_labels: ["kids"]
      channels:
        by_name: ["Disney*", "*Cartoon*", "Nickelodeon*", "*PBS*"]
        by_labels: ["kids"]

  - name: "Exclude Adult"
    type: exclude
    match:
      channels:
        by_labels: ["adult", "xxx"]
`,
  },
};

export default function FilterForm({ filter, onSuccess, onCancel }: FilterFormProps) {
  const [name, setName] = useState(filter?.name || '');
  const [description, setDescription] = useState(filter?.description || '');
  const [rulesYaml, setRulesYaml] = useState(filter?.filter_config || '');
  const [error, setError] = useState<string | null>(null);
  const [showRulesExamples, setShowRulesExamples] = useState(false);
  const [rulesTabIndex, setRulesTabIndex] = useState(0);
  const [tabIndex, setTabIndex] = useState(0);

  // Sync form state when filter changes
  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setName(filter?.name || '');
    setDescription(filter?.description || '');
    setRulesYaml(filter?.filter_config || '');
    setError(null);
    setTabIndex(0);
  }, [filter]);

  const createMutation = useMutation({
    mutationFn: (data: Parameters<typeof filtersApi.createFilter>[0]) =>
      filtersApi.createFilter(data),
    onSuccess: () => {
      onSuccess();
    },
    onError: (err) => {
      setError(getErrorMessage(err));
    },
  });

  const updateMutation = useMutation({
    mutationFn: (data: Parameters<typeof filtersApi.updateFilter>[1]) =>
      filtersApi.updateFilter(filter!.id, data),
    onSuccess: () => {
      onSuccess();
    },
    onError: (err) => {
      setError(getErrorMessage(err));
    },
  });

  const validateYAML = () => {
    try {
      // Rules section is required
      if (!rulesYaml.trim()) {
        setError('Rules configuration is required');
        return false;
      }
      // Check for rules: section
      if (!rulesYaml.includes('rules:')) {
        setError('Rules must start with "rules:" section');
        return false;
      }
      // Check for basic YAML structure
      if (!rulesYaml.includes('- name:') || !rulesYaml.includes('type:')) {
        setError('Rules must have name and type (include/exclude) properties');
        return false;
      }
      // Check for rule type
      if (!rulesYaml.includes('type: include') && !rulesYaml.includes('type: exclude')) {
        setError('Rules must have type "include" or "exclude"');
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
      description: description.trim() || null,
      filter_config: rulesYaml,
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
      <DialogContent sx={{ minWidth: 900, pt: 2 }}>
        {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}

        {/* Main Tabs */}
        <Box sx={{ borderBottom: 1, borderColor: 'divider', mb: 2 }}>
          <Tabs
            value={tabIndex}
            onChange={(_, newValue) => setTabIndex(newValue)}
          >
            <Tab label="Basics" />
            <Tab label="Filter" />
          </Tabs>
        </Box>

        {/* Basics Tab */}
        {tabIndex === 0 && (
          <Box sx={{ p: 2 }}>
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
              rows={4}
            />

            {/* Template Buttons */}
            {!filter && (
              <Box sx={{ mt: 3 }}>
                <Typography variant="body2" sx={{ fontWeight: 500, mb: 1.5 }}>
                  Quick Templates:
                </Typography>
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
          </Box>
        )}

        {/* Filter Tab */}
        {tabIndex === 1 && (
          <Box sx={{ p: 2, display: 'flex', flexDirection: 'column', gap: 2 }}>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
              <Typography variant="body2" sx={{ fontWeight: 500 }}>
                Filter Rules (YAML) *
              </Typography>
              <IconButton
                size="small"
                onClick={() => {
                  setShowRulesExamples(true);
                  setRulesTabIndex(0);
                }}
                title="View configuration examples"
                sx={{ p: 0.5 }}
              >
                <HelpOutlineIcon sx={{ fontSize: '1.2rem' }} />
              </IconButton>
            </Box>
            <Box
              sx={{
                border: '1px solid #ccc',
                borderRadius: 1,
                overflow: 'hidden',
                height: 350,
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
            <Typography variant="caption" sx={{ color: '#999' }}>
              Must start with "rules:" followed by array of rules. Each rule has name, type (include/exclude), and match criteria.
              by_name supports wildcards: * and ?. by_labels uses AND logic (all must match).
              Include rules ACCEPT matching streams, exclude rules REJECT matching streams. Unmatched hidden if rules exist.
            </Typography>
            <Button
              variant="outlined"
              size="small"
              onClick={validateYAML}
              sx={{ alignSelf: 'flex-start' }}
            >
              Validate YAML
            </Button>
          </Box>
        )}
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

      {/* Rules Configuration Examples Modal */}
      <Dialog
        open={showRulesExamples}
        onClose={() => setShowRulesExamples(false)}
        maxWidth="md"
        fullWidth
      >
        <DialogTitle>Filter Rules Configuration Examples</DialogTitle>
        <DialogContent sx={{ minHeight: 600 }}>
          <Tabs
            value={rulesTabIndex}
            onChange={(_, newValue) => setRulesTabIndex(newValue)}
            sx={{ mb: 2, borderBottom: '1px solid #ccc' }}
          >
            {Object.entries(RULES_CONFIG_EXAMPLES).map(([key]) => (
              <Tab key={key} label={RULES_CONFIG_EXAMPLES[key as keyof typeof RULES_CONFIG_EXAMPLES].title} />
            ))}
          </Tabs>

          {Object.entries(RULES_CONFIG_EXAMPLES).map(([key, example], index) => (
            rulesTabIndex === index && (
              <Box key={key}>
                <Typography variant="subtitle2" sx={{ mb: 1, fontWeight: 600 }}>
                  {example.title}
                </Typography>
                <Typography variant="body2" sx={{ mb: 2, color: '#666' }}>
                  {example.description}
                </Typography>
                <Box
                  sx={{
                    border: '1px solid #ddd',
                    borderRadius: 1,
                    backgroundColor: '#f9f9f9',
                    p: 2,
                    mb: 2,
                    maxHeight: 400,
                    overflow: 'auto',
                    fontFamily: 'monospace',
                    fontSize: '0.85rem',
                    whiteSpace: 'pre-wrap',
                    wordBreak: 'break-word',
                  }}
                >
                  {example.yaml}
                </Box>
                <Button
                  variant="outlined"
                  size="small"
                  onClick={() => {
                    setRulesYaml(example.yaml);
                    setShowRulesExamples(false);
                  }}
                >
                  Use This Example
                </Button>
              </Box>
            )
          ))}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setShowRulesExamples(false)}>Close</Button>
        </DialogActions>
      </Dialog>

    </>
  );
}
