# Task ID: 14

**Title:** React Admin Panel - Source & Client Management UI

**Status:** pending

**Dependencies:** 13

**Priority:** medium

**Description:** Build React components for managing sources and clients with forms, tables, and real-time sync status

**Details:**

1. Create API service methods: `src/services/sourcesApi.ts`
   - getSources(), createSource(data), updateSource(id, data), deleteSource(id)
   - testConnection(id), triggerSync(id, taskType), getSyncLogs(id)
2. Create sources pages:
   - src/pages/Sources/SourcesList.tsx
     - MUI DataGrid with columns: ID, Name, URL, Status, Last Sync, Actions
     - Add Source button → opens modal
     - Action buttons: Edit, Delete, Test Connection, Sync
     - Search/filter functionality
   - src/pages/Sources/SourceForm.tsx
     - Modal/drawer with form fields: Name, URL, Username, Password, Sync Interval
     - React Hook Form validation
     - Test Connection button (real-time feedback)
     - Submit creates/updates source
   - src/pages/Sources/SyncStatus.tsx
     - Component showing sync status per task type
     - Progress bars for active syncs
     - Last sync timestamp per task
     - Manual sync buttons per task type or all
3. Create clients API service: `src/services/clientsApi.ts`
   - getClients(), createClient(data), updateClient(id, data), deleteClient(id)
   - getClientLogs(id)
4. Create clients pages:
   - src/pages/Clients/ClientsList.tsx
     - DataGrid: ID, Username, Name, Source, Filter, Active, Expiry, Actions
     - Add Client button → modal
     - Actions: Edit, Delete, View Logs
     - Search and pagination
   - src/pages/Clients/ClientForm.tsx
     - Fields: Username, Password, Name, Email, Source (dropdown), Filter (dropdown, optional)
     - Hide Adult Content toggle (default: enabled)
     - Expiry Date picker
     - Max Connections input
     - Is Active toggle
     - Generate Credentials button (random username/password)
     - Copy credentials to clipboard button
     - Display connection URL: http://proxy-url/player_api.php?username=X&password=Y
   - src/pages/Clients/ClientLogs.tsx
     - Modal showing connection_logs for client
     - Table: Timestamp, Action, IP Address, User Agent
5. Real-time sync status:
   - Use React Query with polling (refetchInterval: 5000)
   - Show sync progress in UI
   - Toast notifications on sync completion/error
6. Form validation:
   - Required field validation
   - URL format validation
   - Email format validation
   - Password strength validation
7. Confirmation dialogs:
   - Delete confirmation with MUI Dialog
   - Warn about cascade deletes

**Test Strategy:**

1. Test sources list displays all sources
2. Test add source form creates new source
3. Test edit source updates existing source
4. Test delete source removes source
5. Test test connection validates credentials
6. Test manual sync triggers sync job
7. Test sync status updates in real-time
8. Test clients list displays all clients
9. Test add client form creates client
10. Test source and filter dropdowns populated correctly
11. Test hide_adult_content toggle defaults to enabled
12. Test generate credentials creates random username/password
13. Test copy credentials to clipboard works
14. Test connection logs display correctly
15. Manual UI testing in browser
