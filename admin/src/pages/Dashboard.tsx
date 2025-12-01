import {
  Box,
  Grid,
  Paper,
  Typography,
  Card,
  CardContent,
} from '@mui/material';
import {
  People as PeopleIcon,
  Storage as StorageIcon,
  FilterList as FilterListIcon,
  LiveTv as LiveTvIcon,
} from '@mui/icons-material';

interface StatCardProps {
  title: string;
  value: string | number;
  icon: React.ReactNode;
  color: string;
}

function StatCard({ title, value, icon, color }: StatCardProps) {
  return (
    <Card>
      <CardContent>
        <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <Box>
            <Typography color="text.secondary" gutterBottom variant="overline">
              {title}
            </Typography>
            <Typography variant="h4" component="div">
              {value}
            </Typography>
          </Box>
          <Box
            sx={{
              backgroundColor: color,
              borderRadius: 2,
              p: 1.5,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
            }}
          >
            {icon}
          </Box>
        </Box>
      </CardContent>
    </Card>
  );
}

export default function Dashboard() {
  return (
    <Box>
      <Typography variant="h4" gutterBottom>
        Dashboard
      </Typography>
      <Typography variant="body1" color="text.secondary" paragraph>
        Welcome to IPTV Organizer Admin Panel
      </Typography>

      <Grid container spacing={3} sx={{ mt: 2 }}>
        <Grid size={{ xs: 12, sm: 6, md: 3 }}>
          <StatCard
            title="Total Sources"
            value="0"
            icon={<StorageIcon sx={{ color: 'white', fontSize: 32 }} />}
            color="#1976d2"
          />
        </Grid>
        <Grid size={{ xs: 12, sm: 6, md: 3 }}>
          <StatCard
            title="Active Clients"
            value="0"
            icon={<PeopleIcon sx={{ color: 'white', fontSize: 32 }} />}
            color="#2e7d32"
          />
        </Grid>
        <Grid size={{ xs: 12, sm: 6, md: 3 }}>
          <StatCard
            title="Filters"
            value="0"
            icon={<FilterListIcon sx={{ color: 'white', fontSize: 32 }} />}
            color="#ed6c02"
          />
        </Grid>
        <Grid size={{ xs: 12, sm: 6, md: 3 }}>
          <StatCard
            title="Total Streams"
            value="0"
            icon={<LiveTvIcon sx={{ color: 'white', fontSize: 32 }} />}
            color="#9c27b0"
          />
        </Grid>
      </Grid>

      <Paper sx={{ mt: 4, p: 3 }}>
        <Typography variant="h6" gutterBottom>
          Quick Start
        </Typography>
        <Typography variant="body2" color="text.secondary" paragraph>
          1. Add a Source to connect to an upstream IPTV provider
        </Typography>
        <Typography variant="body2" color="text.secondary" paragraph>
          2. Create Filters to organize and customize content
        </Typography>
        <Typography variant="body2" color="text.secondary" paragraph>
          3. Add Clients and assign them sources and filters
        </Typography>
        <Typography variant="body2" color="text.secondary">
          4. Clients can now connect using their credentials
        </Typography>
      </Paper>
    </Box>
  );
}
