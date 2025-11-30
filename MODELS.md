# Data Models & ORM Implementation

## Task 3: Data Models & ORM Implementation - Completed ✓

Complete PHP model classes for all database tables with CRUD operations, validation, and relationships.

## Models Created (11 files)

### 1. BaseModel (`src/Models/BaseModel.php`)

Abstract base class providing common functionality for all models.

**Features:**
- CRUD operations: `find()`, `findAll()`, `save()`, `delete()`
- Automatic timestamps (created_at, updated_at)
- Data validation framework
- Magic getters/setters for attributes
- PDO connection management
- Fillable attributes support
- Automatic insert/update detection

**Methods:**
- `find($id)` - Find record by primary key
- `findAll($conditions, $orderBy, $limit, $offset)` - Find multiple records with conditions
- `save()` - Insert or update record
- `delete()` - Delete record
- `validate()` - Override in child classes for validation
- `toArray()` - Get all attributes as array

### 2. AdminUser (`src/Models/AdminUser.php`)

Admin panel user authentication and management.

**Features:**
- Password hashing with bcrypt
- Authentication with username/password
- Last login tracking
- Active/inactive status
- Email validation

**Methods:**
- `authenticate($username, $password)` - Authenticate admin user
- `hashPassword($password)` - Hash password using bcrypt
- `create($username, $password, $email)` - Create new admin user
- `updatePassword($newPassword)` - Update user password
- `isActive()` - Check if user is active
- `activate()` / `deactivate()` - Toggle active status

**Validation:**
- Username required (3-100 characters)
- Email format validation
- Password hash required

### 3. Source (`src/Models/Source.php`)

Upstream IPTV source server management.

**Features:**
- Connection testing
- Sync status tracking
- Next sync time calculation
- Source-to-client relationships

**Methods:**
- `testConnection()` - Test connection to source server
- `updateSyncStatus($status)` - Update sync status (idle, syncing, error)
- `getNextSyncTime()` - Get next scheduled sync time
- `updateNextSyncTime()` - Calculate and update next sync
- `isSyncDue()` - Check if sync is due
- `clients()` - Get all clients for this source
- `syncLogs($limit)` - Get sync logs for this source
- `getActive()` - Get all active sources

**Validation:**
- Name, URL, username, password required
- URL format validation
- Sync interval minimum 60 seconds
- Sync status ENUM validation

### 4. Filter (`src/Models/Filter.php`)

YAML-based stream filtering configuration.

**Features:**
- YAML parsing (placeholder for symfony/yaml integration in Task 9)
- Filter rules extraction
- Favoris configuration
- Configuration validation

**Methods:**
- `parseYaml()` - Parse YAML configuration
- `validateYaml($yaml)` - Validate YAML syntax and structure
- `applyToStreams($streams)` - Apply filter to streams (placeholder)
- `getRules()` - Get filter rules
- `getFavoris()` - Get favoris configuration
- `createFromArray($name, $description, $config)` - Create from array

**Validation:**
- Name and filter_config required
- YAML must contain 'rules:' and 'favoris:' sections

### 5. Client (`src/Models/Client.php`)

End-user client credentials and management.

**Features:**
- Client authentication
- Expiry date checking
- Source and filter assignment
- Connection logging integration
- Credential generation

**Methods:**
- `authenticate($username, $password)` - Authenticate client
- `isExpired()` - Check if client expired
- `isValid()` - Check if active and not expired
- `assignSource($sourceId)` - Assign IPTV source
- `assignFilter($filterId)` - Assign filter
- `source()` - Get client's source (belongsTo)
- `filter()` - Get client's filter (belongsTo)
- `connectionLogs($limit)` - Get connection logs (hasMany)
- `generateCredentials()` - Generate random username/password

**Validation:**
- Username required and unique
- Source assignment required
- Password required
- Email format validation
- Max connections minimum 1

### 6. Category (`src/Models/Category.php`)

Stream categories (live, vod, series).

**Features:**
- Label extraction from category names
- Composite unique key (source_id, category_id, category_type)
- Get or create functionality

**Methods:**
- `getBySourceAndType($sourceId, $type)` - Get categories by source and type
- `extractLabels($name)` - Extract labels from category name
- `findBySourceCategory($sourceId, $categoryId, $type)` - Find by composite key
- `getOrCreate($sourceId, $categoryId, $type, $name, $parentId)` - Get or create category

**Label Extraction:**
- Extracts text between `[` and `]`
- Splits by `-` delimiter
- Splits by `|` delimiter
- Returns comma-separated labels

**Validation:**
- Source ID and category ID required
- Category name required
- Category type must be: live, vod, series

### 7. LiveStream (`src/Models/LiveStream.php`)

Live TV streams with label extraction and Xtream format conversion.

**Features:**
- Label extraction from stream names
- Xtream API format conversion
- JSON data storage
- Get or create functionality

**Methods:**
- `getBySource($sourceId, $activeOnly)` - Get streams by source
- `getByCategory($sourceId, $categoryId)` - Get streams by category
- `extractLabels($text, $streamType)` - Extract labels (static)
- `toXtreamFormat($proxyUrl, $username, $password)` - Convert to Xtream API format
- `findBySourceStream($sourceId, $streamId)` - Find by composite key
- `getOrCreate($sourceId, $streamId, $streamData)` - Get or create stream

**Label Extraction:**
- Extracts text between `[` and `]`
- Splits by `-` and `|` delimiters
- Adds stream type ('live') as label
- Example: "ESPN [HD] | Sports - USA" + "live" → "ESPN,HD,Sports,USA,live"

**Xtream Format:**
```php
[
    'num' => stream_id,
    'name' => stream_name,
    'stream_id' => stream_id,
    'stream_icon' => icon_url,
    'category_id' => category_id,
    'stream_url' => "http://proxy/live/{user}/{pass}/{id}.m3u8",
    // ... other Xtream fields from stored JSON
]
```

**Validation:**
- Source ID, stream ID, name, category ID required

### 8. VodStream (`src/Models/VodStream.php`)

Video-on-demand streams (similar to LiveStream).

**Features:**
- Same as LiveStream but for VOD content
- Stream type: 'movie'
- Stream URL: `/movie/{user}/{pass}/{id}.{ext}`

**Methods:**
- Same as LiveStream
- `extractLabels($text)` - Uses 'movie' stream type

### 9. Series (`src/Models/Series.php`)

TV series content (similar to LiveStream).

**Features:**
- Same as LiveStream but for series content
- Stream type: 'series'
- Includes series-specific metadata (episodes, seasons)

**Methods:**
- Same as LiveStream
- `extractLabels($text)` - Uses 'series' stream type

### 10. SyncLog (`src/Models/SyncLog.php`)

Synchronization operation tracking.

**Features:**
- Track sync operations per task type
- Operation statistics
- Sync status tracking
- Running sync detection

**Sync Types:**
- live_categories
- live_streams
- vod_categories
- vod_streams
- series_categories
- series

**Methods:**
- `logSyncStart($sourceId, $syncType)` - Start sync logging
- `logSyncComplete($status, $stats, $errorMessage)` - Complete sync logging
- `getLatest($sourceId, $syncType)` - Get latest sync log
- `getHistory($sourceId, $syncType, $limit)` - Get sync history
- `isSyncRunning($sourceId, $syncType)` - Check if sync is running
- `getStats($sourceId, $syncType)` - Get sync statistics

**Statistics Tracked:**
- Total syncs, completed, failed
- Items added, updated, deleted

**Validation:**
- Source ID required
- Sync type must be valid ENUM
- Status must be: running, completed, failed

### 11. ConnectionLog (`src/Models/ConnectionLog.php`)

Client connection activity tracking.

**Features:**
- Log client API requests
- IP address and user agent tracking
- Connection statistics
- Old log cleanup

**Methods:**
- `logConnection($clientId, $action, $request)` - Log client connection
- `getRecentByClient($clientId, $limit)` - Get recent connections
- `getByAction($action, $limit)` - Get connections by action
- `getByIp($ipAddress, $limit)` - Get connections by IP
- `getClientStats($clientId, $hours)` - Get connection statistics
- `countRecentConnections($clientId, $minutes)` - Count recent connections
- `cleanOldLogs($daysToKeep)` - Clean old logs
- `client()` - Get client for this log (belongsTo)

**Validation:**
- Client ID, action, IP address required

## Model Relationships

### Source Model
- **hasMany** clients (`clients()`)
- **hasMany** sync_logs (`syncLogs()`)

### Client Model
- **belongsTo** source (`source()`)
- **belongsTo** filter (`filter()`)
- **hasMany** connection_logs (`connectionLogs()`)

### ConnectionLog Model
- **belongsTo** client (`client()`)

### SyncLog Model
- **belongsTo** source (implicit via source_id)

## Usage Examples

### Creating an Admin User

```php
use App\Models\AdminUser;

$admin = AdminUser::create('admin', 'secure_password', 'admin@example.com');
```

### Authenticating a Client

```php
use App\Models\Client;

$client = Client::authenticate('user123', 'pass123');

if ($client && $client->isValid()) {
    // Client authenticated and not expired
    $source = $client->source();
    $filter = $client->filter();
}
```

### Label Extraction

```php
use App\Models\LiveStream;

$labels = LiveStream::extractLabels("ESPN [HD] | Sports - USA", "live");
// Returns: "ESPN,HD,Sports,USA,live"
```

### Converting Stream to Xtream Format

```php
$stream = LiveStream::find(1);
$xtreamData = $stream->toXtreamFormat(
    'http://proxy.example.com',
    'user123',
    'pass123'
);
```

### Logging Sync Operation

```php
use App\Models\SyncLog;

$log = SyncLog::logSyncStart($sourceId, 'live_streams');

// ... perform sync ...

$log->logSyncComplete('completed', [
    'added' => 10,
    'updated' => 5,
    'deleted' => 2
]);
```

### Logging Client Connection

```php
use App\Models\ConnectionLog;

ConnectionLog::logConnection($clientId, 'get_live_streams', [
    'ip' => $_SERVER['REMOTE_ADDR'],
    'user_agent' => $_SERVER['HTTP_USER_AGENT']
]);
```

## Testing Checklist

Once you have a PHP environment:

```php
// Test BaseModel CRUD
$source = new Source();
$source->name = 'Test Source';
$source->url = 'http://example.com';
$source->username = 'user';
$source->password = 'pass';
$source->save();

$found = Source::find($source->id);
echo $found->name; // "Test Source"

// Test relationships
$client = new Client();
$client->assignSource($source->id);
$clientSource = $client->source(); // Returns Source model

// Test validation
try {
    $invalid = new Client();
    $invalid->save(); // Throws RuntimeException
} catch (RuntimeException $e) {
    echo $e->getMessage(); // "Client username is required"
}

// Test label extraction
$labels = LiveStream::extractLabels("ESPN [HD] - Sports", "live");
assert($labels === "ESPN,HD,Sports,live");
```

## Next Steps

Proceed to **Task 4: Xtream Codes API Client Library** to implement the HTTP client for fetching data from upstream servers.
