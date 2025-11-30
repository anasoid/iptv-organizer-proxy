# Task ID: 7

**Title:** Client Authentication & Authorization System

**Status:** pending

**Dependencies:** 3

**Priority:** high

**Description:** Implement client authentication for Xtream API, validate credentials, check expiry, handle source assignment, and log connections

**Details:**

1. Create `src/Middleware/ClientAuthMiddleware.php`:
   - Extract username/password from query parameters
   - Validate credentials against clients table
   - Check client is_active=1
   - Check expiry_date > now()
   - Verify client has source_id assigned
   - Load client's assigned Source model
   - Load client's assigned Filter model (if any)
   - Store client, source, filter in request attributes
   - Return 401 Unauthorized if auth fails
2. Create `src/Services/AuthService.php`:
   - authenticateClient($username, $password)
     - Query clients table
     - Compare password (plain text or hashed, configurable)
     - Return Client model or null
   - isClientActive($client)
     - Check is_active and expiry_date
   - logConnection($client, $action, $request)
     - Insert into connection_logs table
     - Store action, IP address, user agent
3. JWT/Session handling (for admin panel):
   - Create `src/Services/JwtService.php` for admin authentication
   - Methods: generateToken($adminUser), validateToken($token)
   - Store JWT secret in environment
   - Use Firebase JWT library: `composer require firebase/php-jwt`
4. Adult content filtering:
   - In ClientAuthMiddleware, store client's hide_adult_content flag
   - Pass to filter service for automatic exclusion
5. Max connections enforcement:
   - Track active connections per client (optional, future enhancement)
   - Store in Redis or database table
6. Rate limiting (optional):
   - Limit API calls per client per time window
   - Use middleware to track and throttle

**Test Strategy:**

1. Test authentication with valid credentials
2. Test authentication fails with invalid credentials
3. Test expired clients are rejected
4. Test inactive clients are rejected
5. Test clients without source assignment are rejected
6. Test connection logging records all requests
7. Test hide_adult_content flag is passed correctly
8. Test JWT generation and validation for admin auth
9. Integration test: make API call with client credentials
10. Test middleware sets request attributes correctly
