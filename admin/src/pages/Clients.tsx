import { Box, Typography, Paper } from '@mui/material';

export default function Clients() {
  return (
    <Box>
      <Typography variant="h4" gutterBottom>
        Clients
      </Typography>
      <Paper sx={{ p: 3, mt: 2 }}>
        <Typography variant="body1" color="text.secondary">
          Client management coming soon...
        </Typography>
      </Paper>
    </Box>
  );
}
