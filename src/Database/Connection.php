<?php

declare(strict_types=1);

namespace App\Database;

use PDO;
use PDOException;
use RuntimeException;

/**
 * Database Connection Class
 *
 * Implements singleton pattern to manage database connections
 * Supports both MySQL and SQLite databases based on environment configuration
 */
class Connection
{
    private static ?PDO $instance = null;

    /**
     * Private constructor to prevent direct instantiation
     */
    private function __construct()
    {
    }

    /**
     * Get database connection instance
     *
     * @return PDO Database connection
     * @throws RuntimeException If database configuration is invalid or connection fails
     */
    public static function getConnection(): PDO
    {
        if (self::$instance === null) {
            self::$instance = self::createConnection();
        }

        return self::$instance;
    }

    /**
     * Create new database connection based on environment configuration
     *
     * @return PDO Database connection
     * @throws RuntimeException If database type is invalid or connection fails
     */
    private static function createConnection(): PDO
    {
        $dbType = $_ENV['DB_TYPE'] ?? 'mysql';

        try {
            if ($dbType === 'mysql') {
                return self::createMysqlConnection();
            } elseif ($dbType === 'sqlite') {
                return self::createSqliteConnection();
            } else {
                throw new RuntimeException("Unsupported database type: {$dbType}");
            }
        } catch (PDOException $e) {
            throw new RuntimeException(
                "Database connection failed: " . $e->getMessage(),
                (int) $e->getCode(),
                $e
            );
        }
    }

    /**
     * Create MySQL database connection
     *
     * @return PDO MySQL connection
     * @throws PDOException If connection fails
     */
    private static function createMysqlConnection(): PDO
    {
        $host = $_ENV['DB_HOST'] ?? 'localhost';
        $port = $_ENV['DB_PORT'] ?? '3306';
        $dbName = $_ENV['DB_NAME'] ?? '';
        $username = $_ENV['DB_USER'] ?? 'root';
        $password = $_ENV['DB_PASS'] ?? '';

        $dsn = "mysql:host={$host};port={$port};dbname={$dbName};charset=utf8mb4";

        $options = [
            PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
            PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
            PDO::ATTR_EMULATE_PREPARES => false,
            PDO::MYSQL_ATTR_INIT_COMMAND => "SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci"
        ];

        return new PDO($dsn, $username, $password, $options);
    }

    /**
     * Create SQLite database connection
     *
     * @return PDO SQLite connection
     * @throws PDOException If connection fails
     */
    private static function createSqliteConnection(): PDO
    {
        $dbPath = $_ENV['DB_SQLITE_PATH'] ?? 'data/database.sqlite';

        // Create directory if it doesn't exist
        $dir = dirname($dbPath);
        if (!is_dir($dir)) {
            mkdir($dir, 0755, true);
        }

        $dsn = "sqlite:{$dbPath}";

        $options = [
            PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
            PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
            PDO::ATTR_EMULATE_PREPARES => false,
        ];

        $pdo = new PDO($dsn, null, null, $options);

        // Enable foreign key support for SQLite
        $pdo->exec('PRAGMA foreign_keys = ON');

        return $pdo;
    }

    /**
     * Reset connection instance (useful for testing)
     *
     * @return void
     */
    public static function resetConnection(): void
    {
        self::$instance = null;
    }

    /**
     * Prevent cloning of the instance
     */
    private function __clone()
    {
    }

    /**
     * Prevent unserializing of the instance
     */
    public function __wakeup()
    {
        throw new RuntimeException("Cannot unserialize singleton");
    }
}
