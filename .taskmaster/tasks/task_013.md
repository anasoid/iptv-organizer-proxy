# Task ID: 13

**Title:** Admin REST API for Source & Client Management

**Status:** pending

**Dependencies:** 11

**Priority:** medium

**Description:** Implement backend REST API endpoints for managing sources, clients, filters, and admin users with authentication

**Details:**

1. Create `src/Controllers/AdminController.php`:
   - Add AdminAuthMiddleware (JWT validation)
   - Method: login($request, $response)
     - Validate username/password against admin_users table
     - Generate JWT token using JwtService
     - Update last_login timestamp
     - Return token and user info
   - Method: logout($request, $response)
     - Invalidate token (optional, stateless JWT)
   - Method: getCurrentAdmin($request, $response)
     - Return current authenticated admin user
   - Method: changePassword($request, $response)
     - Validate old password
     - Hash and update new password
2. Source management endpoints:
   - GET /api/sources - List all sources (paginated)
   - POST /api/sources - Create source (validate URL, credentials)
   - GET /api/sources/{id} - Get source details
   - PUT /api/sources/{id} - Update source
   - DELETE /api/sources/{id} - Delete source (cascade delete streams)
   - POST /api/sources/{id}/sync - Trigger manual sync (all tasks or specific)
   - POST /api/sources/{id}/test - Test source connection
   - GET /api/sources/{id}/sync-logs - Get sync logs per task type
3. Client management endpoints:
   - GET /api/clients - List clients (paginated, searchable)
   - POST /api/clients - Create client (validate source_id, filter_id)
   - GET /api/clients/{id} - Get client details
   - PUT /api/clients/{id} - Update client
   - DELETE /api/clients/{id} - Delete client
   - GET /api/clients/{id}/logs - Get connection logs
4. Filter management endpoints:
   - GET /api/filters - List filters
   - POST /api/filters - Create filter (validate YAML)
   - GET /api/filters/{id} - Get filter
   - PUT /api/filters/{id} - Update filter
   - DELETE /api/filters/{id} - Delete filter
   - POST /api/filters/{id}/preview - Preview filter results
5. Admin user management endpoints:
   - GET /api/admin-users - List admin users
   - POST /api/admin-users - Create admin user
   - GET /api/admin-users/{id} - Get admin user
   - PUT /api/admin-users/{id} - Update admin user
   - DELETE /api/admin-users/{id} - Delete admin user
6. Dashboard endpoints:
   - GET /api/dashboard/stats - System stats (total sources, clients, streams)
   - GET /api/dashboard/activity - Recent activity logs
   - GET /api/sync/status - Sync status for all sources per task type
7. Request validation:
   - Use middleware to validate request bodies
   - Return 400 Bad Request with validation errors
8. Error handling:
   - Standardized error responses
   - Log all errors

**Test Strategy:**

1. Test login endpoint with valid/invalid credentials
2. Test JWT token generated correctly
3. Test protected endpoints require valid JWT
4. Test source CRUD operations
5. Test client CRUD operations
6. Test filter CRUD operations
7. Test admin user CRUD operations
8. Test manual sync trigger starts sync job
9. Test source connection test validates credentials
10. Test filter preview returns filtered streams
11. Test request validation rejects invalid data
12. Integration test all endpoints with Postman/Insomnia
