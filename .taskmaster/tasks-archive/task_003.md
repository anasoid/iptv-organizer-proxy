# Task ID: 3

**Title:** Data Models & ORM Implementation

**Status:** pending

**Dependencies:** 2

**Priority:** high

**Description:** Create PHP model classes for all database tables with CRUD operations, validation, and relationships

**Details:**

1. Create base Model class: `src/Models/BaseModel.php`
   - Protected $db (PDO connection)
   - Methods: find($id), findAll(), save(), delete(), update()
   - Validation logic
   - Timestamps handling (created_at, updated_at)
2. Create AdminUser model: `src/Models/AdminUser.php`
   - Methods: authenticate($username, $password), hashPassword($password)
   - Validation: username required, email format, password strength
3. Create Source model: `src/Models/Source.php`
   - Methods: testConnection(), updateSyncStatus($status), getNextSyncTime()
   - Relationships: hasMany clients, hasMany sync_logs
   - Validation: URL format, credentials required
4. Create Client model: `src/Models/Client.php`
   - Methods: authenticate(), isExpired(), assignSource($sourceId), assignFilter($filterId)
   - Relationships: belongsTo source, belongsTo filter
   - Validation: unique username, source_id required
5. Create Filter model: `src/Models/Filter.php`
   - Methods: parseYaml(), validateYaml($yaml), applyToStreams($streams)
   - Validation: valid YAML syntax, rules and favoris sections present
6. Create Category model: `src/Models/Category.php`
   - Methods: getBySourceAndType($sourceId, $type), extractLabels($name)
   - Validation: category_type ENUM
7. Create LiveStream model: `src/Models/LiveStream.php`
   - Methods: getBySource($sourceId), getByCategory($sourceId, $categoryId), extractLabels($name)
   - Static method: extractLabels($text) - parse by '-', '|', '[]' delimiters
   - Methods: toXtreamFormat() - convert from database to Xtream API JSON
   - Validation: stream_id unique per source
8. Create VodStream and Series models (similar to LiveStream)
9. Create SyncLog model: `src/Models/SyncLog.php`
   - Methods: logSyncStart($sourceId, $type), logSyncComplete($status, $stats)
   - Validation: sync_type ENUM
10. Create ConnectionLog model: `src/Models/ConnectionLog.php`
    - Methods: logConnection($clientId, $action, $request)

**Test Strategy:**

1. Unit test each model's CRUD operations
2. Test BaseModel find/findAll/save/delete methods
3. Test AdminUser password hashing and authentication
4. Test Source connection testing and sync status updates
5. Test Client authentication and expiry checks
6. Test Filter YAML parsing and validation
7. Test label extraction from channel/category names
8. Test relationships (belongsTo, hasMany) work correctly
9. Test data validation prevents invalid data
10. Test toXtreamFormat() returns correct JSON structure
