<?php

declare(strict_types=1);

namespace App\Models;

use DateTime;
use RuntimeException;

/**
 * Client Model
 *
 * Represents an end-user client with credentials and source assignment
 *
 * @property int $id
 * @property int $source_id
 * @property int|null $filter_id
 * @property string $username
 * @property string $password
 * @property string|null $name
 * @property string|null $email
 * @property string|null $expiry_date
 * @property int $is_active
 * @property int $hide_adult_content
 * @property int $max_connections
 * @property string|null $notes
 * @property string $created_at
 * @property string $updated_at
 */
class Client extends BaseModel
{
    protected string $table = 'clients';
    protected array $fillable = [
        'source_id',
        'filter_id',
        'username',
        'password',
        'name',
        'email',
        'expiry_date',
        'is_active',
        'hide_adult_content',
        'max_connections',
        'notes',
    ];

    /**
     * Authenticate client
     *
     * @param string $username
     * @param string $password
     * @return static|null
     */
    public static function authenticate(string $username, string $password): ?static
    {
        $instance = new static();
        $stmt = $instance->db->prepare(
            "SELECT * FROM {$instance->table} WHERE username = ? AND is_active = 1 LIMIT 1"
        );
        $stmt->execute([$username]);
        $data = $stmt->fetch();

        if (!$data) {
            return null;
        }

        // Verify password (plain text comparison for now)
        // In production, you might want to use hashed passwords
        if ($data['password'] !== $password) {
            return null;
        }

        // Check if expired
        if (!empty($data['expiry_date'])) {
            $expiryDate = new DateTime($data['expiry_date']);
            $now = new DateTime();

            if ($now > $expiryDate) {
                return null; // Client expired
            }
        }

        // Update last_login
        $updateStmt = $instance->db->prepare(
            "UPDATE {$instance->table} SET last_login = ? WHERE id = ?"
        );
        $updateStmt->execute([
            $instance->getCurrentTimestamp(),
            $data['id']
        ]);

        $instance->attributes = $data;
        $instance->exists = true;

        return $instance;
    }

    /**
     * Check if client is expired
     *
     * @return bool
     */
    public function isExpired(): bool
    {
        if (empty($this->expiry_date)) {
            return false; // No expiry date means never expires
        }

        $expiryDate = new DateTime($this->expiry_date);
        $now = new DateTime();

        return $now > $expiryDate;
    }

    /**
     * Check if client is active and not expired
     *
     * @return bool
     */
    public function isValid(): bool
    {
        return $this->isActive() && !$this->isExpired();
    }

    /**
     * Assign source to client
     *
     * @param int $sourceId
     * @return bool
     */
    public function assignSource(int $sourceId): bool
    {
        // Verify source exists
        $source = Source::find($sourceId);
        if (!$source) {
            throw new RuntimeException("Source not found");
        }

        $this->source_id = $sourceId;
        return $this->save();
    }

    /**
     * Assign filter to client
     *
     * @param int|null $filterId
     * @return bool
     */
    public function assignFilter(?int $filterId): bool
    {
        if ($filterId !== null) {
            // Verify filter exists
            $filter = Filter::find($filterId);
            if (!$filter) {
                throw new RuntimeException("Filter not found");
            }
        }

        $this->filter_id = $filterId;
        return $this->save();
    }

    /**
     * Get client's source
     *
     * @return Source|null
     */
    public function source(): ?Source
    {
        if (empty($this->source_id)) {
            return null;
        }

        return Source::find((int) $this->source_id);
    }

    /**
     * Get client's filter
     *
     * @return Filter|null
     */
    public function filter(): ?Filter
    {
        if (empty($this->filter_id)) {
            return null;
        }

        return Filter::find((int) $this->filter_id);
    }

    /**
     * Get connection logs for this client
     *
     * @param int $limit
     * @return array
     */
    public function connectionLogs(int $limit = 100): array
    {
        return ConnectionLog::findAll(
            ['client_id' => $this->id],
            ['created_at' => 'DESC'],
            $limit
        );
    }

    /**
     * Validate model data
     *
     * @return bool
     */
    protected function validate(): bool
    {
        // Username required and unique
        if (empty($this->attributes['username'])) {
            throw new RuntimeException("Client username is required");
        }

        // Check username uniqueness (if inserting)
        if (!$this->exists) {
            $existing = static::findAll(['username' => $this->attributes['username']]);
            if (!empty($existing)) {
                throw new RuntimeException("Username already exists");
            }
        }

        // Source ID required
        if (empty($this->attributes['source_id'])) {
            throw new RuntimeException("Source assignment is required");
        }

        // Password required
        if (empty($this->attributes['password'])) {
            throw new RuntimeException("Password is required");
        }

        // Email format validation (if provided)
        if (!empty($this->attributes['email'])) {
            if (!filter_var($this->attributes['email'], FILTER_VALIDATE_EMAIL)) {
                throw new RuntimeException("Invalid email format");
            }
        }

        // Max connections must be positive
        if (isset($this->attributes['max_connections'])) {
            $maxConn = (int) $this->attributes['max_connections'];
            if ($maxConn < 1) {
                throw new RuntimeException("Max connections must be at least 1");
            }
        }

        return true;
    }

    /**
     * Check if client is active
     *
     * @return bool
     */
    public function isActive(): bool
    {
        return (bool) ($this->attributes['is_active'] ?? false);
    }

    /**
     * Activate client
     *
     * @return bool
     */
    public function activate(): bool
    {
        $this->is_active = 1;
        return $this->save();
    }

    /**
     * Deactivate client
     *
     * @return bool
     */
    public function deactivate(): bool
    {
        $this->is_active = 0;
        return $this->save();
    }

    /**
     * Set expiry date
     *
     * @param string $date Y-m-d H:i:s format
     * @return bool
     */
    public function setExpiryDate(string $date): bool
    {
        $this->expiry_date = $date;
        return $this->save();
    }

    /**
     * Generate random credentials
     *
     * @return array ['username' => string, 'password' => string]
     */
    public static function generateCredentials(): array
    {
        $username = 'user_' . bin2hex(random_bytes(8));
        $password = bin2hex(random_bytes(12));

        return [
            'username' => $username,
            'password' => $password,
        ];
    }
}
