import { Box, Typography, Paper } from '@mui/material';

export default function AdminUsers() {
  return (
    <Box>
      <Typography variant="h4" gutterBottom>
        Admin Users
      </Typography>
      <Paper sx={{ p: 3, mt: 2 }}>
        <Typography variant="body1" color="text.secondary">
          Admin user management coming soon...
        </Typography>
      </Paper>
    </Box>
  );
}
