<?php

declare(strict_types=1);

namespace App\Models;

use RuntimeException;

/**
 * ConnectionLog Model
 *
 * Tracks client connection activity
 */
class ConnectionLog extends BaseModel
{
    protected string $table = 'connection_logs';
    protected array $fillable = [
        'client_id',
        'action',
        'ip_address',
        'user_agent',
    ];
    protected array $dates = ['created_at'];

    /**
     * Log client connection/action
     *
     * @param int $clientId
     * @param string $action API action or endpoint accessed
     * @param array $request Request data (should contain 'ip' and optionally 'user_agent')
     * @return static
     */
    public static function logConnection(int $clientId, string $action, array $request): static
    {
        $instance = new static();
        $instance->client_id = $clientId;
        $instance->action = $action;
        $instance->ip_address = $request['ip'] ?? $_SERVER['REMOTE_ADDR'] ?? 'unknown';
        $instance->user_agent = $request['user_agent'] ?? $_SERVER['HTTP_USER_AGENT'] ?? null;

        if (!$instance->save()) {
            throw new RuntimeException("Failed to create connection log");
        }

        return $instance;
    }

    /**
     * Get recent connections for client
     *
     * @param int $clientId
     * @param int $limit
     * @return array
     */
    public static function getRecentByClient(int $clientId, int $limit = 100): array
    {
        return static::findAll(
            ['client_id' => $clientId],
            ['created_at' => 'DESC'],
            $limit
        );
    }

    /**
     * Get connections by action
     *
     * @param string $action
     * @param int $limit
     * @return array
     */
    public static function getByAction(string $action, int $limit = 100): array
    {
        return static::findAll(
            ['action' => $action],
            ['created_at' => 'DESC'],
            $limit
        );
    }

    /**
     * Get connections by IP address
     *
     * @param string $ipAddress
     * @param int $limit
     * @return array
     */
    public static function getByIp(string $ipAddress, int $limit = 100): array
    {
        return static::findAll(
            ['ip_address' => $ipAddress],
            ['created_at' => 'DESC'],
            $limit
        );
    }

    /**
     * Get connection statistics for client
     *
     * @param int $clientId
     * @param int $hours Number of hours to look back
     * @return array
     */
    public static function getClientStats(int $clientId, int $hours = 24): array
    {
        $instance = new static();

        $dbType = $_ENV['DB_TYPE'] ?? 'mysql';
        $dateSubtract = $dbType === 'sqlite'
            ? "datetime('now', '-{$hours} hours')"
            : "DATE_SUB(NOW(), INTERVAL {$hours} HOUR)";

        $sql = "SELECT
                    action,
                    COUNT(*) as request_count,
                    COUNT(DISTINCT ip_address) as unique_ips
                FROM {$instance->table}
                WHERE client_id = ?
                AND created_at >= {$dateSubtract}
                GROUP BY action
                ORDER BY request_count DESC";

        $stmt = $instance->db->prepare($sql);
        $stmt->execute([$clientId]);

        return $stmt->fetchAll();
    }

    /**
     * Count connections for client in time period
     *
     * @param int $clientId
     * @param int $minutes
     * @return int
     */
    public static function countRecentConnections(int $clientId, int $minutes = 60): int
    {
        $instance = new static();

        $dbType = $_ENV['DB_TYPE'] ?? 'mysql';
        $dateSubtract = $dbType === 'sqlite'
            ? "datetime('now', '-{$minutes} minutes')"
            : "DATE_SUB(NOW(), INTERVAL {$minutes} MINUTE)";

        $sql = "SELECT COUNT(*) FROM {$instance->table}
                WHERE client_id = ? AND created_at >= {$dateSubtract}";

        $stmt = $instance->db->prepare($sql);
        $stmt->execute([$clientId]);

        return (int) $stmt->fetchColumn();
    }

    /**
     * Clean old logs (for maintenance)
     *
     * @param int $daysToKeep
     * @return int Number of deleted records
     */
    public static function cleanOldLogs(int $daysToKeep = 30): int
    {
        $instance = new static();

        $dbType = $_ENV['DB_TYPE'] ?? 'mysql';
        $dateSubtract = $dbType === 'sqlite'
            ? "datetime('now', '-{$daysToKeep} days')"
            : "DATE_SUB(NOW(), INTERVAL {$daysToKeep} DAY)";

        $sql = "DELETE FROM {$instance->table} WHERE created_at < {$dateSubtract}";

        $stmt = $instance->db->prepare($sql);
        $stmt->execute();

        return $stmt->rowCount();
    }

    /**
     * Validate model data
     *
     * @return bool
     */
    protected function validate(): bool
    {
        if (empty($this->attributes['client_id'])) {
            throw new RuntimeException("Client ID is required");
        }

        if (empty($this->attributes['action'])) {
            throw new RuntimeException("Action is required");
        }

        if (empty($this->attributes['ip_address'])) {
            throw new RuntimeException("IP address is required");
        }

        return true;
    }

    /**
     * Get client for this log entry
     *
     * @return Client|null
     */
    public function client(): ?Client
    {
        if (empty($this->client_id)) {
            return null;
        }

        return Client::find((int) $this->client_id);
    }
}
