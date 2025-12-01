import { useState } from 'react';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  Box,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  Tabs,
  Tab,
  Typography,
  CircularProgress,
  Alert,
  Accordion,
  AccordionSummary,
  AccordionDetails,
} from '@mui/material';
import { ExpandMore as ExpandMoreIcon } from '@mui/icons-material';
import { useQuery } from '@tanstack/react-query';
import type { Filter } from '../services/filtersApi';
import filtersApi from '../services/filtersApi';
import sourcesApi from '../services/sourcesApi';

interface FilterPreviewProps {
  open: boolean;
  filter: Filter;
  onClose: () => void;
}

interface TabPanelProps {
  children?: React.ReactNode;
  index: number;
  value: number;
}

interface Source {
  id: number;
  name: string;
}

interface PreviewSample {
  id: string;
  type: 'live' | 'vod' | 'series';
  name: string;
  matched_rules?: string[];
}

function TabPanel(props: TabPanelProps) {
  const { children, value, index, ...other } = props;

  return (
    <div
      role="tabpanel"
      hidden={value !== index}
      id={`preview-tabpanel-${index}`}
      aria-labelledby={`preview-tab-${index}`}
      {...other}
    >
      {value === index && <Box sx={{ p: 2 }}>{children}</Box>}
    </div>
  );
}

export default function FilterPreview({ open, filter, onClose }: FilterPreviewProps) {
  const [selectedSourceId, setSelectedSourceId] = useState<number | ''>('');
  const [tabValue, setTabValue] = useState(0);

  // Fetch sources for dropdown
  const { data: sourcesData } = useQuery({
    queryKey: ['sources', 1, 100],
    queryFn: () => sourcesApi.getSources(1, 100),
  });

  // Fetch preview data
  const { data: previewData, isLoading: isPreviewLoading } = useQuery({
    queryKey: ['filterPreview', filter.id, selectedSourceId],
    queryFn: () =>
      filtersApi.previewFilter(filter.id, selectedSourceId ? Number(selectedSourceId) : undefined),
    enabled: open && Boolean(selectedSourceId),
  });

  const handleSourceChange = (_event: unknown, value: unknown) => {
    setSelectedSourceId(value as number);
  };

  const handleTabChange = (_event: React.SyntheticEvent, newValue: number) => {
    setTabValue(newValue);
  };

  const preview = previewData?.data;

  return (
    <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth>
      <DialogTitle>Filter Preview: {filter.name}</DialogTitle>
      <DialogContent sx={{ minHeight: 400 }}>
        <Box sx={{ mb: 3, mt: 2 }}>
          <FormControl sx={{ minWidth: 200 }}>
            <InputLabel>Select Source</InputLabel>
            <Select
              value={selectedSourceId}
              onChange={handleSourceChange}
              label="Select Source"
            >
              <MenuItem value="">
                <em>Choose a source...</em>
              </MenuItem>
              {sourcesData?.data?.map((source: Source) => (
                <MenuItem key={source.id} value={source.id}>
                  {source.name}
                </MenuItem>
              ))}
            </Select>
          </FormControl>
        </Box>

        {isPreviewLoading ? (
          <Box sx={{ display: 'flex', justifyContent: 'center', p: 4 }}>
            <CircularProgress />
          </Box>
        ) : selectedSourceId ? (
          <>
            {preview ? (
              <>
                <Typography variant="body2" sx={{ mb: 2 }}>
                  Results: {preview.preview_count} streams shown
                </Typography>

                <Tabs value={tabValue} onChange={handleTabChange}>
                  <Tab label="Live Streams" id="preview-tab-0" aria-controls="preview-tabpanel-0" />
                  <Tab label="VOD" id="preview-tab-1" aria-controls="preview-tabpanel-1" />
                  <Tab label="Series" id="preview-tab-2" aria-controls="preview-tabpanel-2" />
                </Tabs>

                <TabPanel value={tabValue} index={0}>
                  {preview.preview_samples?.filter((s: PreviewSample) => s.type === 'live').length > 0 ? (
                    <Box>
                      {preview.preview_samples
                        ?.filter((s: PreviewSample) => s.type === 'live')
                        .slice(0, 20)
                        .map((stream: PreviewSample) => (
                          <Accordion key={stream.id}>
                            <AccordionSummary expandIcon={<ExpandMoreIcon />}>
                              <Typography>{stream.name}</Typography>
                            </AccordionSummary>
                            <AccordionDetails>
                              <Box>
                                <Typography variant="caption" display="block" sx={{ mb: 1 }}>
                                  Matched Rules:
                                </Typography>
                                {(stream.matched_rules ?? []).length > 0 ? (
                                  <Box>
                                    {(stream.matched_rules ?? []).map((rule: string, idx: number) => (
                                      <Typography key={idx} variant="caption" display="block">
                                        • {rule}
                                      </Typography>
                                    ))}
                                  </Box>
                                ) : (
                                  <Typography variant="caption">No rules matched</Typography>
                                )}
                              </Box>
                            </AccordionDetails>
                          </Accordion>
                        ))}
                    </Box>
                  ) : (
                    <Typography variant="body2" color="textSecondary">
                      No live streams match this filter
                    </Typography>
                  )}
                </TabPanel>

                <TabPanel value={tabValue} index={1}>
                  {preview.preview_samples?.filter((s: PreviewSample) => s.type === 'vod').length > 0 ? (
                    <Box>
                      {preview.preview_samples
                        ?.filter((s: PreviewSample) => s.type === 'vod')
                        .slice(0, 20)
                        .map((stream: PreviewSample) => (
                          <Accordion key={stream.id}>
                            <AccordionSummary expandIcon={<ExpandMoreIcon />}>
                              <Typography>{stream.name}</Typography>
                            </AccordionSummary>
                            <AccordionDetails>
                              <Box>
                                <Typography variant="caption" display="block" sx={{ mb: 1 }}>
                                  Matched Rules:
                                </Typography>
                                {(stream.matched_rules ?? []).length > 0 ? (
                                  <Box>
                                    {(stream.matched_rules ?? []).map((rule: string, idx: number) => (
                                      <Typography key={idx} variant="caption" display="block">
                                        • {rule}
                                      </Typography>
                                    ))}
                                  </Box>
                                ) : (
                                  <Typography variant="caption">No rules matched</Typography>
                                )}
                              </Box>
                            </AccordionDetails>
                          </Accordion>
                        ))}
                    </Box>
                  ) : (
                    <Typography variant="body2" color="textSecondary">
                      No VOD streams match this filter
                    </Typography>
                  )}
                </TabPanel>

                <TabPanel value={tabValue} index={2}>
                  {preview.preview_samples?.filter((s: PreviewSample) => s.type === 'series').length > 0 ? (
                    <Box>
                      {preview.preview_samples
                        ?.filter((s: PreviewSample) => s.type === 'series')
                        .slice(0, 20)
                        .map((stream: PreviewSample) => (
                          <Accordion key={stream.id}>
                            <AccordionSummary expandIcon={<ExpandMoreIcon />}>
                              <Typography>{stream.name}</Typography>
                            </AccordionSummary>
                            <AccordionDetails>
                              <Box>
                                <Typography variant="caption" display="block" sx={{ mb: 1 }}>
                                  Matched Rules:
                                </Typography>
                                {(stream.matched_rules ?? []).length > 0 ? (
                                  <Box>
                                    {(stream.matched_rules ?? []).map((rule: string, idx: number) => (
                                      <Typography key={idx} variant="caption" display="block">
                                        • {rule}
                                      </Typography>
                                    ))}
                                  </Box>
                                ) : (
                                  <Typography variant="caption">No rules matched</Typography>
                                )}
                              </Box>
                            </AccordionDetails>
                          </Accordion>
                        ))}
                    </Box>
                  ) : (
                    <Typography variant="body2" color="textSecondary">
                      No series match this filter
                    </Typography>
                  )}
                </TabPanel>
              </>
            ) : (
              <Alert severity="warning">No preview data available for the selected source</Alert>
            )}
          </>
        ) : (
          <Typography variant="body2" color="textSecondary">
            Select a source to preview filtered results
          </Typography>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Close</Button>
      </DialogActions>
    </Dialog>
  );
}
