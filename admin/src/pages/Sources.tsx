import { Box, Typography, Paper } from '@mui/material';

export default function Sources() {
  return (
    <Box>
      <Typography variant="h4" gutterBottom>
        Sources
      </Typography>
      <Paper sx={{ p: 3, mt: 2 }}>
        <Typography variant="body1" color="text.secondary">
          Source management coming soon...
        </Typography>
      </Paper>
    </Box>
  );
}
