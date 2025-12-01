import { Box, Typography, Paper } from '@mui/material';

export default function Filters() {
  return (
    <Box>
      <Typography variant="h4" gutterBottom>
        Filters
      </Typography>
      <Paper sx={{ p: 3, mt: 2 }}>
        <Typography variant="body1" color="text.secondary">
          Filter management coming soon...
        </Typography>
      </Paper>
    </Box>
  );
}
