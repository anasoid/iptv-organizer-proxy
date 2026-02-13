import { useState, useEffect } from 'react';
import {
  Box,
  Button,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Alert,
  Stack,
  IconButton,
  Typography,
  Tabs,
  Tab,
} from '@mui/material';
import HelpOutlineIcon from '@mui/icons-material/HelpOutline';
import Editor from '@monaco-editor/react';
import { useMutation } from '@tanstack/react-query';
import type { AxiosError } from 'axios';
import type { Source } from '../services/sourcesApi';
import sourcesApi from '../services/sourcesApi';

function getErrorMessage(err: unknown): string {
  if (err instanceof Error) {
    const axiosErr = err as AxiosError<{ message?: string; success?: boolean }>;
    if (axiosErr.response?.data?.message) {
      return axiosErr.response.data.message;
    }
    return err.message;
  }
  return 'An unexpected error occurred';
}

interface SourceBlackListFormProps {
  open: boolean;
  onClose: () => void;
  source: Source | null;
  onSuccess: () => void;
}

const BLACKLIST_EXAMPLES = {
  hideAdult: {
    title: 'Hide Adult Content',
    description: 'Automatically hide categories with adult content',
    yaml: `rules:
  - name: "Hide Adult Categories"
    type: exclude
    match:
      categories:
        by_name: ["*Adult*", "*XXX*", "*18+*", "*Porn*"]
        by_labels: ["adult", "18+", "xxx"]`,
  },
  hideNonEnglish: {
    title: 'Hide Non-English',
    description: 'Keep only English language channels',
    yaml: `rules:
  - name: "Keep English Only"
    type: include
    match:
      categories:
        by_name: ["*English*", "*EN*"]
        by_labels: ["english", "en"]`,
  },
  hideLocalSports: {
    title: 'Hide Local Sports',
    description: 'Hide regional sports channels',
    yaml: `rules:
  - name: "Hide Local Sports"
    type: exclude
    match:
      categories:
        by_name: ["*LOCAL*", "*Regional*"]
        by_labels: ["local", "regional"]`,
  },
  hideNews: {
    title: 'Hide News Channels',
    description: 'Hide all news-related categories',
    yaml: `rules:
  - name: "Hide News"
    type: exclude
    match:
      categories:
        by_name: ["*News*", "*BBC*", "*CNN*", "*Reuters*"]
        by_labels: ["news", "breaking"]`,
  },
};

export default function SourceBlackListForm({
  open,
  onClose,
  source,
  onSuccess,
}: SourceBlackListFormProps) {
  const [rulesYaml, setRulesYaml] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [showExamples, setShowExamples] = useState(false);
  const [examplesTabIndex, setExamplesTabIndex] = useState(0);
  const [tabIndex, setTabIndex] = useState(0);

  useEffect(() => {
    const rulesValue = source?.blackListFilter || '';
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setRulesYaml(rulesValue);
    setError(null);
    setTabIndex(0);
  }, [source?.blackListFilter, open]);

  const updateMutation = useMutation({
    mutationFn: (data: Partial<Source>) => sourcesApi.updateSource(source!.id!, data),
    onSuccess: () => {
      onSuccess();
      onClose();
    },
    onError: (err) => {
      setError(getErrorMessage(err));
    },
  });

  const validateYAML = () => {
    try {
      if (!rulesYaml.trim()) {
        setError(null);
        return true;
      }
      if (!rulesYaml.includes('rules:')) {
        setError('Rules must start with "rules:" section');
        return false;
      }
      if (!rulesYaml.includes('- name:') || !rulesYaml.includes('type:')) {
        setError('Rules must have name and type (include/exclude) properties');
        return false;
      }
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

  const handleApplyExample = (exampleKey: keyof typeof BLACKLIST_EXAMPLES) => {
    const example = BLACKLIST_EXAMPLES[exampleKey];
    setRulesYaml(example.yaml);
    setError(null);
  };

  const handleSubmit = async () => {
    if (!validateYAML()) {
      return;
    }

    const data: Partial<Source> = {
      blackListFilter: rulesYaml.trim() || null,
    };

    updateMutation.mutate(data);
  };

  const isLoading = updateMutation.isPending;

  return (
    <>
      <Dialog open={open} onClose={onClose} maxWidth="lg" fullWidth>
        <DialogTitle>Source Blacklist Filter Configuration</DialogTitle>
        <DialogContent sx={{ minWidth: 900, pt: 2 }}>
          {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}

          {/* Main Tabs */}
          <Box sx={{ borderBottom: 1, borderColor: 'divider', mb: 2 }}>
            <Tabs value={tabIndex} onChange={(_, newValue) => setTabIndex(newValue)}>
              <Tab label="Configuration" />
              <Tab label="Information" />
            </Tabs>
          </Box>

          {/* Configuration Tab */}
          {tabIndex === 0 && (
            <Box sx={{ p: 2, display: 'flex', flexDirection: 'column', gap: 2 }}>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                <Typography variant="body2" sx={{ fontWeight: 500 }}>
                  Blacklist Rules (YAML)
                </Typography>
                <IconButton
                  size="small"
                  onClick={() => {
                    setShowExamples(true);
                    setExamplesTabIndex(0);
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
                by_name supports wildcards: * and ?. by_labels uses AND logic.
                Exclude rules will HIDE matching categories. Include rules will SHOW only matching categories. Leave empty to disable.
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

          {/* Information Tab */}
          {tabIndex === 1 && (
            <Box sx={{ p: 2 }}>
              <Stack spacing={2}>
                <Box>
                  <Typography variant="subtitle2" sx={{ fontWeight: 600, mb: 1 }}>
                    How Blacklist Filters Work
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    Blacklist filters are applied during synchronization to automatically hide or show categories based on rules.
                    These rules use the same syntax as client-side filters and can match by category name or labels.
                  </Typography>
                </Box>

                <Box>
                  <Typography variant="subtitle2" sx={{ fontWeight: 600, mb: 1 }}>
                    Rule Types
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    <strong>exclude:</strong> Hides matching categories and their streams from all clients<br/>
                    <strong>include:</strong> Shows ONLY matching categories, hiding all others (restrictive mode)
                  </Typography>
                </Box>

                <Box>
                  <Typography variant="subtitle2" sx={{ fontWeight: 600, mb: 1 }}>
                    Match Criteria
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    <strong>by_name:</strong> Match category names using wildcards (* for any, ? for single char)<br/>
                    <strong>by_labels:</strong> Match category labels (all labels must match for a rule to apply)
                  </Typography>
                </Box>

                <Box>
                  <Typography variant="subtitle2" sx={{ fontWeight: 600, mb: 1 }}>
                    Examples
                  </Typography>
                  <Button
                    variant="outlined"
                    size="small"
                    onClick={() => {
                      setTabIndex(0);
                      setShowExamples(true);
                    }}
                  >
                    View Configuration Examples
                  </Button>
                </Box>
              </Stack>
            </Box>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={onClose} disabled={isLoading}>
            Cancel
          </Button>
          <Button
            onClick={handleSubmit}
            variant="contained"
            disabled={isLoading}
          >
            {isLoading ? 'Saving...' : 'Save Blacklist Configuration'}
          </Button>
        </DialogActions>
      </Dialog>

      {/* Examples Modal */}
      <Dialog
        open={showExamples}
        onClose={() => setShowExamples(false)}
        maxWidth="md"
        fullWidth
      >
        <DialogTitle>Blacklist Filter Examples</DialogTitle>
        <DialogContent sx={{ minHeight: 500 }}>
          <Tabs
            value={examplesTabIndex}
            onChange={(_, newValue) => setExamplesTabIndex(newValue)}
            sx={{ mb: 2, borderBottom: '1px solid #ccc' }}
          >
            {Object.entries(BLACKLIST_EXAMPLES).map(([key]) => (
              <Tab key={key} label={BLACKLIST_EXAMPLES[key as keyof typeof BLACKLIST_EXAMPLES].title} />
            ))}
          </Tabs>

          {Object.entries(BLACKLIST_EXAMPLES).map(([key, example], index) => (
            examplesTabIndex === index && (
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
                    maxHeight: 300,
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
                    handleApplyExample(key as keyof typeof BLACKLIST_EXAMPLES);
                    setShowExamples(false);
                  }}
                >
                  Use This Example
                </Button>
              </Box>
            )
          ))}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setShowExamples(false)}>Close</Button>
        </DialogActions>
      </Dialog>
    </>
  );
}
