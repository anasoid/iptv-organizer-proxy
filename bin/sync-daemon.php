#!/usr/bin/env php
<?php

declare(strict_types=1);

/**
 * Background Sync Daemon
 *
 * Long-running PHP daemon for automatic scheduled synchronization with support
 * for all 6 sync task types per source.
 *
 * Configuration via environment variables:
 *   SYNC_CHECK_INTERVAL    Interval between sync checks in seconds (default: 300)
 *   SYNC_LOCK_TIMEOUT      Lock file timeout in seconds (default: 1800 - 30 minutes)
 *   SYNC_MEMORY_LIMIT      Memory limit before restart in MB (default: 256)
 *   LOG_DIR                Directory for daemon logs (default: logs/sync-daemon)
 *
 * Usage:
 *   php bin/sync-daemon.php [OPTIONS]
 *
 * Options:
 *   --help                 Show this help message
 *   --dry-run              Run one iteration without actual syncs
 *   --verbose              Show detailed output
 */

require_once __DIR__ . '/../bootstrap.php';

use App\Models\Source;
use App\Models\SyncSchedule;
use App\Models\SyncLog;
use App\Services\Xtream\XtreamClient;
use App\Services\SyncService;
use Monolog\Logger;
use Monolog\Handlers\StreamHandler;
use Monolog\Handlers\RotatingFileHandler;
use Monolog\Formatter\LineFormatter;

// Parse command-line arguments
$options = getopt('', ['help', 'dry-run', 'verbose']);

// Show help
if (isset($options['help'])) {
    echo "Background Sync Daemon\n";
    echo "======================\n\n";
    echo "Long-running daemon for automatic IPTV source synchronization\n\n";
    echo "Usage:\n";
    echo "  php bin/sync-daemon.php [OPTIONS]\n\n";
    echo "Options:\n";
    echo "  --help                 Show this help message\n";
    echo "  --dry-run              Run one iteration without actual syncs\n";
    echo "  --verbose              Show detailed output\n\n";
    echo "Environment Variables:\n";
    echo "  SYNC_CHECK_INTERVAL    Check interval in seconds (default: 300)\n";
    echo "  SYNC_LOCK_TIMEOUT      Lock timeout in seconds (default: 1800)\n";
    echo "  SYNC_MEMORY_LIMIT      Memory limit in MB before restart (default: 256)\n";
    echo "  LOG_DIR                Log directory (default: logs/sync-daemon)\n\n";
    echo "Examples:\n";
    echo "  php bin/sync-daemon.php\n";
    echo "  php bin/sync-daemon.php --dry-run\n";
    echo "  php bin/sync-daemon.php --verbose\n\n";
    exit(0);
}

$dryRun = isset($options['dry-run']);
$verbose = isset($options['verbose']);

// Configuration
$checkInterval = (int) ($_ENV['SYNC_CHECK_INTERVAL'] ?? 300);
$lockTimeout = (int) ($_ENV['SYNC_LOCK_TIMEOUT'] ?? 1800);
$memoryLimit = (int) ($_ENV['SYNC_MEMORY_LIMIT'] ?? 256);
$logDir = $_ENV['LOG_DIR'] ?? __DIR__ . '/../logs/sync-daemon';

// Ensure log directory exists
if (!is_dir($logDir)) {
    mkdir($logDir, 0755, true);
}

// Initialize logger
$logger = new Logger('SyncDaemon');

// Add rotating file handler (daily rotation)
$fileHandler = new RotatingFileHandler(
    $logDir . '/sync-daemon.log',
    7,  // Keep 7 days of logs
    Logger::INFO
);
$formatter = new LineFormatter(
    "[%datetime%] %level_name%: %message%\n",
    'Y-m-d H:i:s'
);
$fileHandler->setFormatter($formatter);
$logger->pushHandler($fileHandler);

// Add stdout handler for INFO and above
$stdoutHandler = new StreamHandler('php://stdout', Logger::INFO);
$stdoutHandler->setFormatter($formatter);
$logger->pushHandler($stdoutHandler);

// State variables
$running = true;
$lastHeartbeat = 0;
$iteration = 0;

/**
 * Signal handlers for graceful shutdown
 */
$shutdownHandler = function (int $signal) use (&$running, $logger) {
    $signalName = match ($signal) {
        SIGTERM => 'SIGTERM',
        SIGINT => 'SIGINT',
        default => "Signal {$signal}",
    };

    $logger->info("Received {$signalName}, gracefully shutting down...");
    $running = false;
};

// Register signal handlers
if (function_exists('pcntl_signal')) {
    pcntl_signal(SIGTERM, $shutdownHandler);
    pcntl_signal(SIGINT, $shutdownHandler);
    pcntl_async_signals(true);
} else {
    $logger->warning("PCNTL extension not available, graceful shutdown may not work");
}

/**
 * Get lock file path for source and task type
 */
function getLockFilePath(int $sourceId, string $taskType): string
{
    return "/tmp/sync-{$sourceId}-{$taskType}.lock";
}

/**
 * Acquire lock for source and task type
 */
function acquireLock(int $sourceId, string $taskType, int $timeout): bool
{
    $lockFile = getLockFilePath($sourceId, $taskType);

    // Check if lock exists
    if (file_exists($lockFile)) {
        $lockTime = (int) file_get_contents($lockFile);
        $age = time() - $lockTime;

        // If lock is expired, remove it
        if ($age > $timeout) {
            unlink($lockFile);
        } else {
            // Lock still valid
            return false;
        }
    }

    // Create lock file
    return (bool) file_put_contents($lockFile, (string) time());
}

/**
 * Release lock for source and task type
 */
function releaseLock(int $sourceId, string $taskType): void
{
    $lockFile = getLockFilePath($sourceId, $taskType);
    if (file_exists($lockFile)) {
        unlink($lockFile);
    }
}

/**
 * Update health check heartbeat
 */
function updateHeartbeat(): bool
{
    return (bool) file_put_contents('/tmp/sync-daemon-heartbeat', (string) time());
}

/**
 * Check memory usage
 */
function getMemoryUsageMB(): float
{
    return memory_get_usage(true) / 1024 / 1024;
}

/**
 * Map task type to SyncService method name
 */
function getTaskMethod(string $taskType): string
{
    return match ($taskType) {
        'live_categories' => 'syncLiveCategories',
        'live_streams' => 'syncLiveStreams',
        'vod_categories' => 'syncVodCategories',
        'vod_streams' => 'syncVodStreams',
        'series_categories' => 'syncSeriesCategories',
        'series' => 'syncSeries',
        default => throw new RuntimeException("Unknown task type: {$taskType}"),
    };
}

// Main daemon loop
$logger->info("Starting sync daemon (check interval: {$checkInterval}s)");

if ($dryRun) {
    $logger->info("Running in DRY-RUN mode");
}

while ($running) {
    $iteration++;
    $loopStart = microtime(true);

    try {
        // Update heartbeat
        updateHeartbeat();
        $lastHeartbeat = time();

        // Log iteration start
        $logger->debug("Iteration {$iteration} started");

        // Get all active sources
        $sources = Source::getActive();

        if (empty($sources)) {
            $logger->debug("No active sources found, waiting...");
        } else {
            $logger->info("Processing " . count($sources) . " source(s)");

            foreach ($sources as $source) {
                if (!$running) {
                    break;
                }

                try {
                    // Get all schedules for this source
                    $schedules = SyncSchedule::findBySource($source->id);

                    if (empty($schedules)) {
                        // Initialize schedules for this source
                        if ($verbose) {
                            $logger->debug("Initializing sync schedule for source {$source->id}");
                        }
                        SyncSchedule::initializeForSource($source->id, $source->sync_interval);
                        $schedules = SyncSchedule::findBySource($source->id);
                    }

                    // Check each task type
                    foreach ($schedules as $schedule) {
                        if (!$running) {
                            break;
                        }

                        try {
                            // Check if sync is due
                            if (!$schedule->isSyncDue()) {
                                if ($verbose) {
                                    $logger->debug(
                                        "Sync not due for {$source->name}/{$schedule->task_type}, " .
                                        "next: {$schedule->next_sync}"
                                    );
                                }
                                continue;
                            }

                            // Try to acquire lock
                            if (!acquireLock($source->id, $schedule->task_type, $lockTimeout)) {
                                $logger->debug(
                                    "Lock exists for {$source->name}/{$schedule->task_type}, skipping"
                                );
                                continue;
                            }

                            // Log sync start
                            $syncLog = SyncLog::logSyncStart($source->id, $schedule->task_type);
                            $logger->info(
                                "Starting sync: {$source->name}/{$schedule->task_type}"
                            );

                            if ($dryRun) {
                                $logger->info("DRY-RUN: Would sync {$source->name}/{$schedule->task_type}");
                                $syncLog->logSyncComplete(SyncLog::STATUS_COMPLETED, [
                                    'added' => 0,
                                    'updated' => 0,
                                    'deleted' => 0,
                                ]);
                                releaseLock($source->id, $schedule->task_type);
                                continue;
                            }

                            // Perform actual sync
                            try {
                                // Create Xtream client
                                $client = new XtreamClient($source);

                                // Authenticate
                                $client->authenticate();

                                // Create sync service
                                $syncService = new SyncService($source, $client, $logger);

                                // Get task method
                                $method = getTaskMethod($schedule->task_type);

                                // Run sync
                                $stats = $syncService->$method();

                                // Log completion
                                $syncLog->logSyncComplete(SyncLog::STATUS_COMPLETED, $stats);
                                $schedule->updateNextSync();

                                $logger->info(
                                    "Completed sync: {$source->name}/{$schedule->task_type} " .
                                    "(added: {$stats['added']}, updated: {$stats['updated']}, " .
                                    "deleted: {$stats['deleted']})"
                                );

                                releaseLock($source->id, $schedule->task_type);

                            } catch (Exception $e) {
                                // Log error
                                $errorMsg = $e->getMessage();
                                $syncLog->logSyncComplete(
                                    SyncLog::STATUS_FAILED,
                                    [],
                                    $errorMsg
                                );

                                $logger->error(
                                    "Sync failed for {$source->name}/{$schedule->task_type}: {$errorMsg}"
                                );

                                releaseLock($source->id, $schedule->task_type);
                            }

                        } catch (Exception $e) {
                            $logger->error(
                                "Error processing {$source->name}/{$schedule->task_type}: " .
                                $e->getMessage()
                            );
                        }
                    }

                } catch (Exception $e) {
                    $logger->error("Error processing source {$source->id}: " . $e->getMessage());
                }
            }
        }

        // Memory management
        $memoryUsage = getMemoryUsageMB();
        $logger->debug(sprintf("Memory usage: %.2f MB", $memoryUsage));

        if ($memoryUsage > $memoryLimit) {
            $logger->warning(
                sprintf(
                    "Memory limit exceeded (%.2f MB > %d MB), restarting daemon",
                    $memoryUsage,
                    $memoryLimit
                )
            );
            break;
        }

        // Log iteration completion
        $loopDuration = microtime(true) - $loopStart;
        $logger->debug(sprintf("Iteration %d completed in %.2f seconds", $iteration, $loopDuration));

        // Sleep for check interval (in dry-run, exit after one iteration)
        if ($dryRun) {
            $logger->info("DRY-RUN: Exiting after one iteration");
            break;
        }

        if ($running) {
            sleep($checkInterval);
        }

    } catch (Exception $e) {
        $logger->error("Fatal error in main loop: " . $e->getMessage());
        $logger->error("Stack trace: " . $e->getTraceAsString());

        if (!$running) {
            break;
        }

        // Wait before retrying
        sleep(5);
    }
}

// Cleanup
$logger->info("Sync daemon stopping");
exit(0);
