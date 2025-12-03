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
  Dialog,
  IconButton,
  Tabs,
  Tab,
  Typography,
} from '@mui/material';
import HelpOutlineIcon from '@mui/icons-material/HelpOutline';
import Editor from '@monaco-editor/react';
import { useMutation } from '@tanstack/react-query';
import type { Filter } from '../services/filtersApi';
import filtersApi from '../services/filtersApi';

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

const FAVORIS_CONFIG_EXAMPLES = {
  sports: {
    title: 'Sports Organization',
    description: 'Organize sports content by type and region',
    yaml: `- name: "Live Sports Events"
  target_group: "Live Sports"
  match:
    channels:
      by_name: ["ESPN*", "Fox*Sports*", "*League*"]
      by_labels: ["live", "sports"]

- name: "Regional Sports"
  target_group: "Regional"
  match:
    channels:
      by_name: ["*Sports*"]
      by_labels: ["sports", "regional"]

- name: "Premium Sports"
  target_group: "Premium"
  match:
    channels:
      by_name: ["DAZN*", "BeIN*"]
      by_labels: ["premium", "sports"]`,
  },
  entertainment: {
    title: 'Entertainment Categories',
    description: 'Organize movies and shows by genre',
    yaml: `- name: "Movies - Action"
  target_group: "Action"
  match:
    channels:
      by_name: ["*Action*", "*Thriller*"]
      by_labels: ["action"]

- name: "Movies - Comedy"
  target_group: "Comedy"
  match:
    channels:
      by_name: ["*Comedy*"]
      by_labels: ["comedy"]

- name: "TV Series"
  target_group: "Series"
  match:
    channels:
      by_name: ["*Series*", "*Episode*"]
      by_labels: ["series"]`,
  },
  family: {
    title: 'Family Content',
    description: 'Organize family-friendly content by age group',
    yaml: `- name: "Kids Cartoons"
  target_group: "Cartoons"
  match:
    channels:
      by_name: ["Disney*", "*Cartoon*", "Nickelodeon*"]
      by_labels: ["kids"]

- name: "Family Movies"
  target_group: "Family"
  match:
    channels:
      by_name: ["*Family*", "*Kids*"]
      by_labels: ["family", "kids"]

- name: "Educational"
  target_group: "Educational"
  match:
    channels:
      by_name: ["PBS*", "*Educational*"]
      by_labels: ["educational"]`,
  },
  regional: {
    title: 'Regional News',
    description: 'Organize news channels by region',
    yaml: `- name: "International News"
  target_group: "World News"
  match:
    channels:
      by_name: ["BBC*", "France*", "Al Jazeera*", "Reuters*", "Euro*"]
      by_labels: ["international"]

- name: "Local News"
  target_group: "Local"
  match:
    channels:
      by_name: ["*Local*", "*Regional*"]
      by_labels: ["local"]

- name: "Breaking News"
  target_group: "Breaking"
  match:
    channels:
      by_labels: ["breaking", "live"]`,
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
    favoris: `- name: "Family Friendly"
  target_group: "Kids & Family"
  match:
    channels:
      by_name: ["Disney*", "*Nickelodeon*", "*Cartoon*", "PBS*"]
    categories:
      by_name: ["*Kids*", "*Family*"]
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
    favoris: `- name: "Premium Sports"
  target_group: "Live Sports"
  match:
    channels:
      by_name: ["ESPN*", "Fox*Sports*", "*League*"]
      by_labels: ["live", "hd"]

- name: "Regional Sports"
  target_group: "Regional"
  match:
    channels:
      by_name: ["*Sports*"]
      by_labels: ["sports", "regional"]
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
    favoris: `- name: "4K Premium"
  target_group: "4K Ultra HD"
  match:
    channels:
      by_name: ["*4K*", "*Ultra*", "*UHD*"]
      by_labels: ["4k"]

- name: "Full HD"
  target_group: "1080p HD"
  match:
    channels:
      by_name: ["*1080*", "*FHD*"]
      by_labels: ["hd"]
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
    favoris: `- name: "Premium Kids"
  target_group: "Premium Kids"
  match:
    channels:
      by_name: ["Disney*", "Nickelodeon*", "*PBS*"]
      by_labels: ["kids", "hd"]

- name: "All Kids Cartoons"
  target_group: "Cartoons"
  match:
    channels:
      by_name: ["*Cartoon*", "*Anime*"]
      by_labels: ["kids"]
`,
  },
};

export default function FilterForm({ filter, onSuccess, onCancel }: FilterFormProps) {
  const [name, setName] = useState(filter?.name || '');
  const [description, setDescription] = useState(filter?.description || '');
  const [rulesYaml, setRulesYaml] = useState(filter?.filter_config || '');
  const [favorisYaml, setFavorisYaml] = useState(filter?.favoris || '');
  const [error, setError] = useState<string | null>(null);
  const [showRulesExamples, setShowRulesExamples] = useState(false);
  const [showFavorisExamples, setShowFavorisExamples] = useState(false);
  const [rulesTabIndex, setRulesTabIndex] = useState(0);
  const [favorisTabIndex, setFavorisTabIndex] = useState(0);

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
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
            <p style={{ margin: '8px 0', fontSize: '0.875rem', fontWeight: 500 }}>
              Filter Rules (YAML) *
            </p>
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
            Must start with "rules:" followed by array of rules. Each rule has name, type (include/exclude), and match criteria.
            by_name supports wildcards: * and ?. by_labels uses AND logic (all must match).
            Include rules ACCEPT matching streams, exclude rules REJECT matching streams. Unmatched ignored if include rules exist.
          </p>
        </Box>

        {/* Favoris YAML Editor */}
        <Box sx={{ mt: 3, mb: 2 }}>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
            <p style={{ margin: '8px 0', fontSize: '0.875rem', fontWeight: 500 }}>
              Virtual Categories / Favoris (YAML)
            </p>
            <IconButton
              size="small"
              onClick={() => {
                setShowFavorisExamples(true);
                setFavorisTabIndex(0);
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
            Array of virtual categories (no "favoris:" wrapper). Each has name, target_group (display name), and match criteria.
            Generated IDs start at 100000 (first favoris), 100001 (second), etc. Stored separately from rules.
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

      {/* Favoris Configuration Examples Modal */}
      <Dialog
        open={showFavorisExamples}
        onClose={() => setShowFavorisExamples(false)}
        maxWidth="md"
        fullWidth
      >
        <DialogTitle>Virtual Categories / Favoris Configuration Examples</DialogTitle>
        <DialogContent sx={{ minHeight: 600 }}>
          <Tabs
            value={favorisTabIndex}
            onChange={(_, newValue) => setFavorisTabIndex(newValue)}
            sx={{ mb: 2, borderBottom: '1px solid #ccc' }}
          >
            {Object.entries(FAVORIS_CONFIG_EXAMPLES).map(([key]) => (
              <Tab key={key} label={FAVORIS_CONFIG_EXAMPLES[key as keyof typeof FAVORIS_CONFIG_EXAMPLES].title} />
            ))}
          </Tabs>

          {Object.entries(FAVORIS_CONFIG_EXAMPLES).map(([key, example], index) => (
            favorisTabIndex === index && (
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
                    setFavorisYaml(example.yaml);
                    setShowFavorisExamples(false);
                  }}
                >
                  Use This Example
                </Button>
              </Box>
            )
          ))}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setShowFavorisExamples(false)}>Close</Button>
        </DialogActions>
      </Dialog>
    </>
  );
}
