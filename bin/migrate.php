#!/usr/bin/env php
<?php

declare(strict_types=1);

/**
 * Database Migration Runner
 *
 * Runs database migrations for both MySQL and SQLite
 * Tracks executed migrations to prevent duplicate runs
 */

require_once __DIR__ . '/../bootstrap.php';

use App\Database\Connection;

// Colors for terminal output
const COLOR_GREEN = "\033[0;32m";
const COLOR_RED = "\033[0;31m";
const COLOR_YELLOW = "\033[1;33m";
const COLOR_RESET = "\033[0m";

function log_success(string $message): void
{
    echo COLOR_GREEN . "✓ " . $message . COLOR_RESET . PHP_EOL;
}

function log_error(string $message): void
{
    echo COLOR_RED . "✗ " . $message . COLOR_RESET . PHP_EOL;
}

function log_info(string $message): void
{
    echo COLOR_YELLOW . "→ " . $message . COLOR_RESET . PHP_EOL;
}

try {
    log_info("Starting database migrations...");

    // Get database connection
    $pdo = Connection::getConnection();
    $dbType = $_ENV['DB_TYPE'] ?? 'mysql';

    log_info("Database type: " . strtoupper($dbType));

    // Determine migration suffix based on database type
    $suffix = $dbType === 'sqlite' ? '_sqlite.sql' : '.sql';

    // Migration directory
    $migrationDir = __DIR__ . '/../migrations';

    if (!is_dir($migrationDir)) {
        log_error("Migrations directory not found: {$migrationDir}");
        exit(1);
    }

    // Create migrations tracking table first
    log_info("Creating migrations tracking table...");
    $trackingFile = $migrationDir . '/000_create_migrations_table' . $suffix;

    if (!file_exists($trackingFile)) {
        log_error("Tracking table migration not found: {$trackingFile}");
        exit(1);
    }

    $sql = file_get_contents($trackingFile);
    $pdo->exec($sql);
    log_success("Migrations tracking table created");

    // Get list of executed migrations
    $stmt = $pdo->query("SELECT migration FROM migrations");
    $executedMigrations = $stmt->fetchAll(PDO::FETCH_COLUMN);

    log_info("Found " . count($executedMigrations) . " previously executed migrations");

    // Get all migration files
    $files = glob($migrationDir . '/*' . $suffix);
    sort($files);

    // Filter to get only pending migrations
    $pendingMigrations = [];
    foreach ($files as $file) {
        $filename = basename($file, $suffix);

        // Skip the tracking table migration (already executed)
        if ($filename === '000_create_migrations_table') {
            continue;
        }

        if (!in_array($filename, $executedMigrations)) {
            $pendingMigrations[] = $file;
        }
    }

    if (empty($pendingMigrations)) {
        log_success("No pending migrations. Database is up to date!");
        exit(0);
    }

    log_info("Found " . count($pendingMigrations) . " pending migrations");

    // Execute pending migrations
    $executed = 0;
    $failed = 0;

    foreach ($pendingMigrations as $file) {
        $filename = basename($file, $suffix);

        log_info("Executing migration: {$filename}");

        try {
            // Read migration file
            $sql = file_get_contents($file);

            if (empty($sql)) {
                log_error("Migration file is empty: {$filename}");
                $failed++;
                continue;
            }

            // Begin transaction
            $pdo->beginTransaction();

            // Execute migration
            $pdo->exec($sql);

            // Record migration in tracking table
            $stmt = $pdo->prepare("INSERT INTO migrations (migration) VALUES (?)");
            $stmt->execute([$filename]);

            // Commit transaction
            $pdo->commit();

            log_success("Migration executed: {$filename}");
            $executed++;

        } catch (PDOException $e) {
            // Rollback on error
            if ($pdo->inTransaction()) {
                $pdo->rollBack();
            }

            log_error("Migration failed: {$filename}");
            log_error("Error: " . $e->getMessage());
            $failed++;

            // Stop on first error
            log_error("Stopping migration process due to error");
            break;
        }
    }

    // Summary
    echo PHP_EOL;
    log_info("Migration summary:");
    log_success("Executed: {$executed}");

    if ($failed > 0) {
        log_error("Failed: {$failed}");
        exit(1);
    }

    log_success("All migrations completed successfully!");
    exit(0);

} catch (Exception $e) {
    log_error("Migration error: " . $e->getMessage());
    log_error("Stack trace: " . $e->getTraceAsString());
    exit(1);
}
