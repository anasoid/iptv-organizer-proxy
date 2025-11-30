<?php

declare(strict_types=1);

namespace App\Models;

use RuntimeException;

/**
 * AdminUser Model
 *
 * Handles admin panel user authentication and management
 */
class AdminUser extends BaseModel
{
    protected string $table = 'admin_users';
    protected array $fillable = [
        'username',
        'password_hash',
        'email',
        'is_active',
    ];

    /**
     * Authenticate admin user
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

        // Verify password
        if (!password_verify($password, $data['password_hash'])) {
            return null;
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
     * Hash password using bcrypt
     *
     * @param string $password
     * @return string
     */
    public static function hashPassword(string $password): string
    {
        return password_hash($password, PASSWORD_BCRYPT);
    }

    /**
     * Create new admin user
     *
     * @param string $username
     * @param string $password
     * @param string|null $email
     * @return static
     */
    public static function create(string $username, string $password, ?string $email = null): static
    {
        $instance = new static();
        $instance->username = $username;
        $instance->password_hash = static::hashPassword($password);
        $instance->email = $email;
        $instance->is_active = 1;

        if (!$instance->save()) {
            throw new RuntimeException("Failed to create admin user");
        }

        return $instance;
    }

    /**
     * Update password
     *
     * @param string $newPassword
     * @return bool
     */
    public function updatePassword(string $newPassword): bool
    {
        $this->password_hash = static::hashPassword($newPassword);
        return $this->save();
    }

    /**
     * Validate model data
     *
     * @return bool
     */
    protected function validate(): bool
    {
        // Username required
        if (empty($this->attributes['username'])) {
            throw new RuntimeException("Username is required");
        }

        // Username length
        if (strlen($this->attributes['username']) < 3 || strlen($this->attributes['username']) > 100) {
            throw new RuntimeException("Username must be between 3 and 100 characters");
        }

        // Email format validation (if provided)
        if (!empty($this->attributes['email'])) {
            if (!filter_var($this->attributes['email'], FILTER_VALIDATE_EMAIL)) {
                throw new RuntimeException("Invalid email format");
            }
        }

        // Password hash required
        if (empty($this->attributes['password_hash'])) {
            throw new RuntimeException("Password hash is required");
        }

        return true;
    }

    /**
     * Check if user is active
     *
     * @return bool
     */
    public function isActive(): bool
    {
        return (bool) ($this->attributes['is_active'] ?? false);
    }

    /**
     * Activate user
     *
     * @return bool
     */
    public function activate(): bool
    {
        $this->is_active = 1;
        return $this->save();
    }

    /**
     * Deactivate user
     *
     * @return bool
     */
    public function deactivate(): bool
    {
        $this->is_active = 0;
        return $this->save();
    }
}
