import { ToggleButton, ToggleButtonGroup, Box } from '@mui/material';
import { ViewList as ListIcon, GridView as GridIcon } from '@mui/icons-material';

type ViewMode = 'list' | 'grid';

interface ViewToggleProps {
  view: ViewMode;
  onChange: (view: ViewMode) => void;
}

export default function ViewToggle({ view, onChange }: ViewToggleProps) {
  return (
    <Box sx={{ display: 'flex', gap: 1 }}>
      <ToggleButtonGroup
        value={view}
        exclusive
        onChange={(_, newView) => {
          if (newView !== null) {
            onChange(newView as ViewMode);
          }
        }}
        size="small"
      >
        <ToggleButton value="list" aria-label="list view">
          <ListIcon />
        </ToggleButton>
        <ToggleButton value="grid" aria-label="grid view">
          <GridIcon />
        </ToggleButton>
      </ToggleButtonGroup>
    </Box>
  );
}

export type { ViewMode };
