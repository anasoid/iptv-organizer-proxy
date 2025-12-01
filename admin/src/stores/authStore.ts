import { create } from 'zustand';
import { authApi } from '../services/api';
import type { AdminUser, LoginRequest } from '../types';

interface AuthState {
  user: AdminUser | null;
  token: string | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  error: string | null;
  login: (credentials: LoginRequest) => Promise<void>;
  logout: () => Promise<void>;
  checkAuth: () => void;
  clearError: () => void;
}

export const useAuthStore = create<AuthState>((set) => ({
  user: null,
  token: null,
  isAuthenticated: false,
  isLoading: true, // Start with true so ProtectedRoute waits for auth check
  error: null,

  login: async (credentials: LoginRequest) => {
    try {
      set({ isLoading: true, error: null });

      const response = await authApi.login(credentials);

      // Store token and user in localStorage
      localStorage.setItem('auth_token', response.token);
      localStorage.setItem('auth_user', JSON.stringify(response.user));

      set({
        user: response.user,
        token: response.token,
        isAuthenticated: true,
        isLoading: false,
        error: null,
      });
    } catch (error) {
      const errorMessage = error instanceof Error && 'response' in error
        ? (error as { response?: { data?: { message?: string } } }).response?.data?.message
        : 'Login failed';
      set({
        user: null,
        token: null,
        isAuthenticated: false,
        isLoading: false,
        error: errorMessage || 'Login failed',
      });
      throw error;
    }
  },

  logout: async () => {
    try {
      await authApi.logout();
    } finally {
      // Clear state regardless of API call result
      localStorage.removeItem('auth_token');
      localStorage.removeItem('auth_user');
      set({
        user: null,
        token: null,
        isAuthenticated: false,
        error: null,
      });
    }
  },

  checkAuth: () => {
    const token = localStorage.getItem('auth_token');
    const userStr = localStorage.getItem('auth_user');

    if (token && userStr) {
      try {
        const user = JSON.parse(userStr);
        set({
          user,
          token,
          isAuthenticated: true,
          isLoading: false,
        });
      } catch {
        // Invalid stored data, clear it
        localStorage.removeItem('auth_token');
        localStorage.removeItem('auth_user');
        set({ isLoading: false });
      }
    } else {
      // No stored auth data
      set({ isLoading: false });
    }
  },

  clearError: () => set({ error: null }),
}));
