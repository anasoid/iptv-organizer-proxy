import { useEffect, useState, useMemo } from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { Box, ThemeProvider, CssBaseline } from '@mui/material';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { createAppTheme } from './utils/theme';
import { useAuthStore } from './stores/authStore';
import ProtectedRoute from './components/ProtectedRoute';
import MainLayout from './components/Layout/MainLayout';
import Login from './pages/Login';
import Dashboard from './pages/Dashboard';
import Sources from './pages/Sources';
import Clients from './pages/Clients';
import Filters from './pages/Filters';
import AdminUsers from './pages/AdminUsers';
import NotFound from './pages/NotFound';

// Create React Query client
const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      refetchOnWindowFocus: false,
      retry: 1,
    },
  },
});

function App() {
  const checkAuth = useAuthStore((state) => state.checkAuth);
  const [themeMode, setThemeMode] = useState<'light' | 'dark'>(() => {
    const saved = localStorage.getItem('theme_mode');
    return (saved as 'light' | 'dark') || 'light';
  });

  const theme = useMemo(() => createAppTheme(themeMode), [themeMode]);

  useEffect(() => {
    // Check for existing authentication on mount
    checkAuth();
  }, []);

  const toggleTheme = () => {
    setThemeMode((prevMode) => {
      const newMode = prevMode === 'light' ? 'dark' : 'light';
      localStorage.setItem('theme_mode', newMode);
      return newMode;
    });
  };

  return (
    <QueryClientProvider client={queryClient}>
      <ThemeProvider theme={theme}>
        <CssBaseline />
        <Box sx={{ width: '100%', height: '100%' }}>
          <BrowserRouter>
            <Routes>
              {/* Public routes */}
              <Route path="/login" element={<Login />} />

              {/* Protected routes */}
              <Route
                element={
                  <ProtectedRoute>
                    <MainLayout themeMode={themeMode} toggleTheme={toggleTheme} />
                  </ProtectedRoute>
                }
              >
                <Route path="/" element={<Navigate to="/dashboard" replace />} />
                <Route path="/dashboard" element={<Dashboard />} />
                <Route path="/sources" element={<Sources />} />
                <Route path="/clients" element={<Clients />} />
                <Route path="/filters" element={<Filters />} />
                <Route path="/admin-users" element={<AdminUsers />} />
              </Route>

              {/* 404 */}
              <Route path="*" element={<NotFound />} />
            </Routes>
          </BrowserRouter>
        </Box>
      </ThemeProvider>
    </QueryClientProvider>
  );
}

export default App;
