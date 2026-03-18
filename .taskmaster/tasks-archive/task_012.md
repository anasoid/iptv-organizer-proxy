# Task ID: 12

**Title:** React Admin Panel - Foundation & Authentication UI

**Status:** pending

**Dependencies:** 7

**Priority:** medium

**Description:** Initialize React application with TypeScript, Material-UI, routing, authentication pages, and protected routes

**Details:**

1. Create React app:
   - `npm create vite@latest admin-panel -- --template react-ts`
   - cd admin-panel && npm install
2. Install dependencies:
   - UI library: `npm install @mui/material @mui/icons-material @emotion/react @emotion/styled`
   - Routing: `npm install react-router-dom`
   - State management: `npm install zustand`
   - HTTP client: `npm install axios`
   - Form handling: `npm install react-hook-form`
   - Data fetching: `npm install @tanstack/react-query`
3. Project structure:
   - src/components/ (reusable UI components)
   - src/pages/ (page components)
   - src/services/ (API services)
   - src/stores/ (Zustand stores)
   - src/types/ (TypeScript types)
   - src/hooks/ (custom hooks)
   - src/utils/ (utility functions)
4. Create authentication store: `src/stores/authStore.ts`
   - State: user, token, isAuthenticated
   - Actions: login(username, password), logout(), checkAuth()
5. Create API service: `src/services/api.ts`
   - Axios instance with base URL
   - Request interceptor: add JWT token to headers
   - Response interceptor: handle 401 errors, redirect to login
   - Methods: login(username, password), logout(), getCurrentAdmin()
6. Create pages:
   - src/pages/Login.tsx
     - Username/password form with React Hook Form
     - Submit calls authStore.login()
     - Redirect to dashboard on success
     - Show error message on failure
   - src/pages/Dashboard.tsx
     - Main dashboard with stats
     - Protected route
   - src/pages/NotFound.tsx (404 page)
7. Create layout: `src/components/Layout/MainLayout.tsx`
   - Sidebar navigation (Dashboard, Sources, Clients, Filters, Admin Users)
   - Top app bar with user info and logout button
   - Main content area
   - Dark/light theme toggle
8. Routing: `src/App.tsx`
   - React Router setup
   - Protected routes wrapper
   - Route definitions:
     - /login (public)
     - / → redirect to /dashboard
     - /dashboard (protected)
     - /sources (protected)
     - /clients (protected)
     - /filters (protected)
     - /admin-users (protected)
9. Protected route component:
   - Check if user authenticated
   - Redirect to /login if not authenticated
10. Theme configuration:
    - Create MUI theme with custom colors
    - Dark/light mode support

**Test Strategy:**

1. Test React app builds successfully
2. Test login page renders correctly
3. Test login form validation works
4. Test successful login redirects to dashboard
5. Test failed login shows error message
6. Test protected routes redirect to login when not authenticated
7. Test logout clears authentication state
8. Test sidebar navigation links work
9. Test theme toggle switches between light/dark mode
10. Test responsive layout works on mobile/tablet
11. Run Jest tests for components
12. Test TypeScript compilation has no errors
