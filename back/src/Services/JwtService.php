<?php

declare(strict_types=1);

namespace App\Services;

use App\Models\AdminUser;
use Firebase\JWT\JWT;
use Firebase\JWT\Key;
use Exception;

/**
 * JWT Service
 *
 * Handles JWT token generation and validation for admin authentication
 */
class JwtService
{
    private string $secret;
    private string $algorithm = 'HS256';
    private int $expirationTime = 86400; // 24 hours in seconds

    /**
     * Constructor
     *
     * @param string|null $secret JWT secret from environment
     */
    public function __construct(?string $secret = null)
    {
        $this->secret = $secret ?? $_ENV['JWT_SECRET'] ?? 'default-secret-change-in-production';

        if ($this->secret === 'default-secret-change-in-production') {
            error_log('WARNING: Using default JWT secret. Set JWT_SECRET in environment.');
        }
    }

    /**
     * Generate JWT token for admin user
     *
     * @param AdminUser $adminUser
     * @return string JWT token
     */
    public function generateToken(AdminUser $adminUser): string
    {
        $issuedAt = time();
        $expirationTime = $issuedAt + $this->expirationTime;

        $payload = [
            'iat' => $issuedAt,
            'exp' => $expirationTime,
            'sub' => $adminUser->id,
            'username' => $adminUser->username,
            'email' => $adminUser->email,
        ];

        return JWT::encode($payload, $this->secret, $this->algorithm);
    }

    /**
     * Validate and decode JWT token
     *
     * @param string $token JWT token
     * @return object|null Decoded token payload or null if invalid
     */
    public function validateToken(string $token): ?object
    {
        try {
            $decoded = JWT::decode($token, new Key($this->secret, $this->algorithm));
            return $decoded;
        } catch (Exception $e) {
            error_log('JWT validation failed: ' . $e->getMessage());
            return null;
        }
    }

    /**
     * Extract admin user ID from token
     *
     * @param string $token JWT token
     * @return int|null Admin user ID or null if invalid
     */
    public function getUserIdFromToken(string $token): ?int
    {
        $decoded = $this->validateToken($token);

        if ($decoded === null || !isset($decoded->sub)) {
            return null;
        }

        return (int) $decoded->sub;
    }

    /**
     * Check if token is expired
     *
     * @param string $token JWT token
     * @return bool True if expired or invalid
     */
    public function isTokenExpired(string $token): bool
    {
        $decoded = $this->validateToken($token);

        if ($decoded === null) {
            return true;
        }

        if (!isset($decoded->exp)) {
            return true;
        }

        return time() >= $decoded->exp;
    }

    /**
     * Set token expiration time
     *
     * @param int $seconds Expiration time in seconds
     * @return void
     */
    public function setExpirationTime(int $seconds): void
    {
        $this->expirationTime = $seconds;
    }
}
