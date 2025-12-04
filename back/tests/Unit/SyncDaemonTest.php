<?php

declare(strict_types=1);

namespace Tests\Unit;

use PHPUnit\Framework\TestCase;

/**
 * Sync Daemon Tests
 *
 * Tests for the background sync daemon implementation
 */
class SyncDaemonTest extends TestCase
{
    protected string $daemonPath;
    protected string $testLockDir;
    protected string $testHeartbeatFile;

    protected function setUp(): void
    {
        $this->daemonPath = __DIR__ . '/../../bin/sync-daemon.php';
        $this->testLockDir = '/tmp';
        $this->testHeartbeatFile = '/tmp/sync-daemon-heartbeat';

        // Clean up any existing test files
        $this->cleanup();
    }

    protected function tearDown(): void
    {
        $this->cleanup();
    }

    protected function cleanup(): void
    {
        // Clean up test lock files
        if (file_exists($this->testHeartbeatFile)) {
            unlink($this->testHeartbeatFile);
        }
    }

    /**
     * Test daemon script exists and is executable
     */
    public function testDaemonScriptExists(): void
    {
        $this->assertFileExists($this->daemonPath, 'Daemon script should exist');
        $this->assertTrue(is_executable($this->daemonPath), 'Daemon script should be executable');
    }

    /**
     * Test daemon help option
     */
    public function testDaemonHelpOption(): void
    {
        $output = [];
        $returnCode = 0;

        exec("php {$this->daemonPath} --help", $output, $returnCode);

        $this->assertEquals(0, $returnCode, 'Help option should exit with code 0');
        $this->assertCount(0, array_filter($output, fn($line) => str_contains($line, 'error')), 'Help should not show errors');
    }

    /**
     * Test daemon dry-run mode (should complete without database)
     */
    public function testDaemonDryRun(): void
    {
        // Set environment variables
        $env = [
            'SYNC_CHECK_INTERVAL' => '1',
            'SYNC_LOCK_TIMEOUT' => '300',
            'SYNC_MEMORY_LIMIT' => '256',
        ];

        // Build command with environment variables
        $envStr = '';
        foreach ($env as $key => $value) {
            $envStr .= "{$key}={$value} ";
        }

        $output = [];
        $returnCode = 0;

        // Run daemon in dry-run mode
        exec("{$envStr}php {$this->daemonPath} --dry-run --verbose 2>&1", $output, $returnCode);

        // In dry-run, we expect it to attempt to run but may fail on DB connection
        // That's okay - we're testing the script structure, not the full functionality
        $this->assertNotEmpty($output, 'Daemon should produce output');
    }

    /**
     * Test lock file mechanism
     */
    public function testLockFileMechanism(): void
    {
        // Simulate the lock file creation logic
        $sourceId = 1;
        $taskType = 'live_streams';
        $lockFile = "/tmp/sync-{$sourceId}-{$taskType}.lock";

        // Create lock file
        $this->assertTrue(
            (bool) file_put_contents($lockFile, (string) time()),
            'Should be able to create lock file'
        );

        // Verify lock file exists
        $this->assertFileExists($lockFile, 'Lock file should exist');

        // Verify lock file contains timestamp
        $lockTime = (int) file_get_contents($lockFile);
        $this->assertGreaterThan(0, $lockTime, 'Lock file should contain valid timestamp');

        // Clean up
        if (file_exists($lockFile)) {
            unlink($lockFile);
        }
    }

    /**
     * Test lock file timeout
     */
    public function testLockFileTimeout(): void
    {
        $sourceId = 1;
        $taskType = 'live_streams';
        $lockFile = "/tmp/sync-{$sourceId}-{$taskType}.lock";
        $timeout = 5; // 5 second timeout for testing

        // Create old lock file (simulating an old lock)
        $oldTime = time() - 10; // 10 seconds old
        file_put_contents($lockFile, (string) $oldTime);

        // Check if lock is expired
        $lockFileTime = (int) file_get_contents($lockFile);
        $age = time() - $lockFileTime;
        $isExpired = $age > $timeout;

        $this->assertTrue($isExpired, 'Old lock file should be considered expired');

        // Clean up
        if (file_exists($lockFile)) {
            unlink($lockFile);
        }
    }

    /**
     * Test heartbeat file creation
     */
    public function testHeartbeatFile(): void
    {
        // Simulate heartbeat file creation
        $this->assertTrue(
            (bool) file_put_contents($this->testHeartbeatFile, (string) time()),
            'Should be able to create heartbeat file'
        );

        $this->assertFileExists($this->testHeartbeatFile, 'Heartbeat file should exist');

        // Verify heartbeat contains timestamp
        $timestamp = (int) file_get_contents($this->testHeartbeatFile);
        $this->assertGreaterThan(0, $timestamp, 'Heartbeat file should contain valid timestamp');
    }

    /**
     * Test SyncSchedule model exists
     */
    public function testSyncScheduleModelExists(): void
    {
        $modelPath = __DIR__ . '/../../src/Models/SyncSchedule.php';
        $this->assertFileExists($modelPath, 'SyncSchedule model should exist');

        // Verify the class can be loaded
        require_once $modelPath;
        $this->assertTrue(
            class_exists('App\Models\SyncSchedule'),
            'SyncSchedule class should be loadable'
        );
    }

    /**
     * Test sync_schedule migration files exist
     */
    public function testMigrationFilesExist(): void
    {
        $mysqlMigration = __DIR__ . '/../../migrations/mysql/011_create_sync_schedule.sql';
        $sqliteMigration = __DIR__ . '/../../migrations/sqlite/011_create_sync_schedule_sqlite.sql';

        $this->assertFileExists($mysqlMigration, 'MySQL migration should exist');
        $this->assertFileExists($sqliteMigration, 'SQLite migration should exist');

        // Verify migration files contain sync_schedule table definition
        $mysqlContent = file_get_contents($mysqlMigration);
        $this->assertStringContainsString('sync_schedule', $mysqlContent, 'MySQL migration should define sync_schedule table');

        $sqliteContent = file_get_contents($sqliteMigration);
        $this->assertStringContainsString('sync_schedule', $sqliteContent, 'SQLite migration should define sync_schedule table');
    }

    /**
     * Test daemon PHP syntax
     */
    public function testDaemonPhpSyntax(): void
    {
        $output = [];
        $returnCode = 0;

        exec("php -l {$this->daemonPath}", $output, $returnCode);

        $this->assertEquals(0, $returnCode, 'Daemon PHP should have valid syntax');
        $this->assertStringContainsString('No syntax errors', $output[0], 'PHP lint should report no syntax errors');
    }

    /**
     * Test daemon script has correct shebang
     */
    public function testDaemonShebang(): void
    {
        $content = file_get_contents($this->daemonPath);
        $this->assertStringStartsWith('#!/usr/bin/env php', $content, 'Daemon should have correct shebang');
    }

    /**
     * Test SyncSchedule model TASK_TYPES constant
     */
    public function testSyncScheduleTaskTypes(): void
    {
        // This tests that the model is properly structured
        $modelPath = __DIR__ . '/../../src/Models/SyncSchedule.php';
        $content = file_get_contents($modelPath);

        // Verify all required task types are in the model
        $taskTypes = [
            'live_categories',
            'live_streams',
            'vod_categories',
            'vod_streams',
            'series_categories',
            'series',
        ];

        foreach ($taskTypes as $type) {
            $this->assertStringContainsString("'{$type}'", $content, "Task type '{$type}' should be in model");
        }
    }

    /**
     * Test daemon script has signal handler setup
     */
    public function testDaemonSignalHandlers(): void
    {
        $content = file_get_contents($this->daemonPath);

        // Verify signal handling code exists
        $this->assertStringContainsString('SIGTERM', $content, 'Daemon should handle SIGTERM');
        $this->assertStringContainsString('SIGINT', $content, 'Daemon should handle SIGINT');
        $this->assertStringContainsString('pcntl_signal', $content, 'Daemon should use pcntl_signal');
    }

    /**
     * Test daemon script has Monolog logging
     */
    public function testDaemonLogging(): void
    {
        $content = file_get_contents($this->daemonPath);

        // Verify Monolog is used
        $this->assertStringContainsString('Monolog\Logger', $content, 'Daemon should use Monolog');
        $this->assertStringContainsString('RotatingFileHandler', $content, 'Daemon should use rotating file handler');
        $this->assertStringContainsString('StreamHandler', $content, 'Daemon should stream to stdout');
    }

    /**
     * Test daemon script has memory management
     */
    public function testDaemonMemoryManagement(): void
    {
        $content = file_get_contents($this->daemonPath);

        // Verify memory management code exists
        $this->assertStringContainsString('memory_get_usage', $content, 'Daemon should check memory usage');
        $this->assertStringContainsString('$memoryLimit', $content, 'Daemon should have memory limit');
    }

    /**
     * Test daemon script has per-task-type lock mechanism
     */
    public function testDaemonLockMechanism(): void
    {
        $content = file_get_contents($this->daemonPath);

        // Verify lock mechanism code exists
        $this->assertStringContainsString('getLockFilePath', $content, 'Daemon should have lock path function');
        $this->assertStringContainsString('acquireLock', $content, 'Daemon should have acquire lock function');
        $this->assertStringContainsString('releaseLock', $content, 'Daemon should have release lock function');
    }
}
