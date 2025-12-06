<?php

declare(strict_types=1);

/**
 * System Information Page
 *
 * Displays comprehensive technical information about the system
 * for debugging and monitoring purposes.
 *
 * Access: http://localhost:9090/info.php
 */

// Security: Restrict access to localhost only in production
$allowedIPs = ['127.0.0.1', 'localhost', '::1'];
$clientIP = $_SERVER['REMOTE_ADDR'] ?? '';

if (!in_array($clientIP, $allowedIPs) && php_sapi_name() !== 'cli') {
    http_response_code(403);
    die('Access restricted to localhost only');
}

?>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>System Information</title>
    <style>
        * {
            margin: 0;
            padding: 0;
            box-sizing: border-box;
        }
        body {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, Cantarell, sans-serif;
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            color: #333;
            padding: 20px;
            min-height: 100vh;
        }
        .container {
            max-width: 1200px;
            margin: 0 auto;
        }
        .header {
            background: white;
            border-radius: 8px;
            padding: 30px;
            margin-bottom: 20px;
            box-shadow: 0 2px 10px rgba(0,0,0,0.1);
        }
        .header h1 {
            color: #667eea;
            margin-bottom: 10px;
        }
        .header p {
            color: #666;
            font-size: 14px;
        }
        .info-grid {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
            gap: 20px;
            margin-bottom: 20px;
        }
        .info-card {
            background: white;
            border-radius: 8px;
            padding: 20px;
            box-shadow: 0 2px 10px rgba(0,0,0,0.1);
        }
        .info-card h2 {
            color: #667eea;
            font-size: 16px;
            margin-bottom: 15px;
            border-bottom: 2px solid #667eea;
            padding-bottom: 10px;
        }
        .info-item {
            display: flex;
            justify-content: space-between;
            padding: 8px 0;
            border-bottom: 1px solid #f0f0f0;
            font-size: 14px;
        }
        .info-item:last-child {
            border-bottom: none;
        }
        .info-label {
            font-weight: 600;
            color: #666;
            flex: 0 0 40%;
        }
        .info-value {
            color: #333;
            flex: 1;
            text-align: right;
            word-break: break-word;
        }
        .status-good {
            color: #27ae60;
            font-weight: 600;
        }
        .status-warning {
            color: #f39c12;
            font-weight: 600;
        }
        .status-critical {
            color: #e74c3c;
            font-weight: 600;
        }
        .full-width {
            grid-column: 1 / -1;
        }
        .phpinfo-section {
            background: white;
            border-radius: 8px;
            padding: 20px;
            box-shadow: 0 2px 10px rgba(0,0,0,0.1);
            margin-top: 20px;
        }
        .section-title {
            color: #667eea;
            font-size: 18px;
            font-weight: 600;
            margin-bottom: 15px;
            border-bottom: 2px solid #667eea;
            padding-bottom: 10px;
        }
        pre {
            background: #f5f5f5;
            padding: 15px;
            border-radius: 4px;
            overflow-x: auto;
            font-size: 12px;
            line-height: 1.5;
        }
        table {
            width: 100%;
            border-collapse: collapse;
            font-size: 13px;
            margin: 10px 0;
        }
        th, td {
            padding: 10px;
            text-align: left;
            border-bottom: 1px solid #ddd;
        }
        th {
            background: #667eea;
            color: white;
            font-weight: 600;
        }
        tr:nth-child(even) {
            background: #f9f9f9;
        }
    </style>
</head>
<body>
    <div class="container">
        <div class="header">
            <h1>🔧 System Information</h1>
            <p>IPTV Organizer Proxy - Technical Status Report</p>
            <p>Timestamp: <?php echo date('Y-m-d H:i:s e'); ?></p>
        </div>

        <div class="info-grid">
            <!-- Server Information -->
            <div class="info-card">
                <h2>Server</h2>
                <div class="info-item">
                    <span class="info-label">OS</span>
                    <span class="info-value"><?php echo php_uname('s'); ?></span>
                </div>
                <div class="info-item">
                    <span class="info-label">Hostname</span>
                    <span class="info-value"><?php echo gethostname() ?: 'N/A'; ?></span>
                </div>
                <div class="info-item">
                    <span class="info-label">Server</span>
                    <span class="info-value"><?php echo $_SERVER['SERVER_SOFTWARE'] ?? 'N/A'; ?></span>
                </div>
                <div class="info-item">
                    <span class="info-label">Client IP</span>
                    <span class="info-value"><?php echo $clientIP; ?></span>
                </div>
            </div>

            <!-- PHP Information -->
            <div class="info-card">
                <h2>PHP</h2>
                <div class="info-item">
                    <span class="info-label">Version</span>
                    <span class="info-value"><?php echo PHP_VERSION; ?></span>
                </div>
                <div class="info-item">
                    <span class="info-label">Memory Limit</span>
                    <span class="info-value"><?php echo ini_get('memory_limit'); ?></span>
                </div>
                <div class="info-item">
                    <span class="info-label">Max Upload</span>
                    <span class="info-value"><?php echo ini_get('upload_max_filesize'); ?></span>
                </div>
                <div class="info-item">
                    <span class="info-label">Max POST</span>
                    <span class="info-value"><?php echo ini_get('post_max_size'); ?></span>
                </div>
                <div class="info-item">
                    <span class="info-label">SAPI</span>
                    <span class="info-value"><?php echo PHP_SAPI; ?></span>
                </div>
            </div>

            <!-- Memory Information -->
            <div class="info-card">
                <h2>Memory</h2>
                <div class="info-item">
                    <span class="info-label">Peak Usage</span>
                    <span class="info-value"><?php echo format_bytes(memory_get_peak_usage(true)); ?></span>
                </div>
                <div class="info-item">
                    <span class="info-label">Current Usage</span>
                    <span class="info-value"><?php echo format_bytes(memory_get_usage(true)); ?></span>
                </div>
                <div class="info-item">
                    <span class="info-label">Limit</span>
                    <span class="info-value"><?php echo ini_get('memory_limit'); ?></span>
                </div>
                <?php
                $memLimit = parse_bytes(ini_get('memory_limit'));
                $memUsage = memory_get_usage(true);
                $memPercent = ($memUsage / $memLimit) * 100;
                $statusClass = $memPercent > 80 ? 'status-critical' : ($memPercent > 60 ? 'status-warning' : 'status-good');
                ?>
                <div class="info-item">
                    <span class="info-label">Usage</span>
                    <span class="info-value"><span class="<?php echo $statusClass; ?>"><?php echo round($memPercent, 1); ?>%</span></span>
                </div>
            </div>

            <!-- Storage Information -->
            <div class="info-card">
                <h2>Storage</h2>
                <?php
                $diskFree = disk_free_space('/');
                $diskTotal = disk_total_space('/');
                $diskUsed = $diskTotal - $diskFree;
                $diskPercent = ($diskUsed / $diskTotal) * 100;
                $diskStatus = $diskPercent > 90 ? 'status-critical' : ($diskPercent > 75 ? 'status-warning' : 'status-good');
                ?>
                <div class="info-item">
                    <span class="info-label">Total</span>
                    <span class="info-value"><?php echo format_bytes($diskTotal); ?></span>
                </div>
                <div class="info-item">
                    <span class="info-label">Used</span>
                    <span class="info-value"><?php echo format_bytes($diskUsed); ?></span>
                </div>
                <div class="info-item">
                    <span class="info-label">Free</span>
                    <span class="info-value"><?php echo format_bytes($diskFree); ?></span>
                </div>
                <div class="info-item">
                    <span class="info-label">Usage</span>
                    <span class="info-value"><span class="<?php echo $diskStatus; ?>"><?php echo round($diskPercent, 1); ?>%</span></span>
                </div>
            </div>

            <!-- PHP Extensions -->
            <div class="info-card">
                <h2>Extensions</h2>
                <?php
                $extensions = get_loaded_extensions();
                $importantExt = ['pdo_sqlite', 'pdo_mysql', 'json', 'curl', 'openssl', 'mbstring', 'fileinfo'];
                ?>
                <div class="info-item">
                    <span class="info-label">Total</span>
                    <span class="info-value"><?php echo count($extensions); ?></span>
                </div>
                <?php foreach ($importantExt as $ext): ?>
                <div class="info-item">
                    <span class="info-label"><?php echo $ext; ?></span>
                    <span class="info-value">
                        <?php if (in_array($ext, $extensions)): ?>
                            <span class="status-good">✓ Loaded</span>
                        <?php else: ?>
                            <span class="status-critical">✗ Missing</span>
                        <?php endif; ?>
                    </span>
                </div>
                <?php endforeach; ?>
            </div>

            <!-- Execution Information -->
            <div class="info-card">
                <h2>Execution</h2>
                <div class="info-item">
                    <span class="info-label">Execution Time</span>
                    <span class="info-value"><?php echo round((microtime(true) - $_SERVER['REQUEST_TIME_FLOAT']) * 1000, 2); ?>ms</span>
                </div>
                <div class="info-item">
                    <span class="info-label">Max Execution</span>
                    <span class="info-value"><?php echo ini_get('max_execution_time'); ?>s</span>
                </div>
                <div class="info-item">
                    <span class="info-label">Default TZ</span>
                    <span class="info-value"><?php echo ini_get('date.timezone'); ?></span>
                </div>
                <div class="info-item">
                    <span class="info-label">Display Errors</span>
                    <span class="info-value"><?php echo ini_get('display_errors') ? 'On' : 'Off'; ?></span>
                </div>
            </div>
        </div>

        <!-- PHP Configuration Table -->
        <div class="phpinfo-section">
            <div class="section-title">📋 PHP Configuration</div>
            <table>
                <thead>
                    <tr>
                        <th>Setting</th>
                        <th>Value</th>
                    </tr>
                </thead>
                <tbody>
                    <?php
                    $importantSettings = [
                        'memory_limit',
                        'post_max_size',
                        'upload_max_filesize',
                        'max_execution_time',
                        'default_socket_timeout',
                        'output_buffering',
                        'display_errors',
                        'error_reporting',
                        'date.timezone',
                        'zlib.output_compression',
                    ];
                    foreach ($importantSettings as $setting): ?>
                    <tr>
                        <td><strong><?php echo $setting; ?></strong></td>
                        <td><code><?php echo ini_get($setting) ?: '(not set)'; ?></code></td>
                    </tr>
                    <?php endforeach; ?>
                </tbody>
            </table>
        </div>

        <!-- Loaded Extensions -->
        <div class="phpinfo-section">
            <div class="section-title">🔌 Loaded Extensions (<?php echo count(get_loaded_extensions()); ?>)</div>
            <pre><?php echo implode("\n", get_loaded_extensions()); ?></pre>
        </div>

        <!-- Environment Variables -->
        <div class="phpinfo-section">
            <div class="section-title">🌍 Environment Variables</div>
            <table>
                <thead>
                    <tr>
                        <th>Variable</th>
                        <th>Value</th>
                    </tr>
                </thead>
                <tbody>
                    <?php
                    $envVars = [
                        'APP_ENV',
                        'APP_DEBUG',
                        'APP_URL',
                        'DB_TYPE',
                        'SYNC_ENABLED',
                        'DEFAULT_SYNC_INTERVAL',
                        'LOG_LEVEL',
                    ];
                    foreach ($envVars as $var): ?>
                    <tr>
                        <td><strong><?php echo $var; ?></strong></td>
                        <td><code><?php echo $_ENV[$var] ?? '(not set)'; ?></code></td>
                    </tr>
                    <?php endforeach; ?>
                </tbody>
            </table>
        </div>

        <!-- Full phpinfo() Output -->
        <div class="phpinfo-section full-width">
            <div class="section-title">ℹ️ Full PHP Info</div>
            <details>
                <summary style="cursor: pointer; padding: 10px; background: #f5f5f5; border-radius: 4px; font-weight: 600;">Click to expand full phpinfo() output</summary>
                <div style="margin-top: 15px;">
                    <?php
                    ob_start();
                    phpinfo();
                    $phpinfo = ob_get_clean();

                    // Strip HTML tags and style, keep just the output
                    $phpinfo = preg_replace('#<style[^>]*>.*?</style>#si', '', $phpinfo);
                    $phpinfo = strip_tags($phpinfo, '<h2><table><tr><th><td>');
                    ?>
                    <pre style="background: #f5f5f5; padding: 15px; border-radius: 4px; font-size: 12px;"><?php echo htmlspecialchars($phpinfo); ?></pre>
                </div>
            </details>
        </div>
    </div>

    <?php
    /**
     * Helper function to format bytes to human-readable format
     */
    function format_bytes($bytes) {
        $units = ['B', 'KB', 'MB', 'GB'];
        $bytes = max($bytes, 0);
        $pow = floor(($bytes ? log($bytes) : 0) / log(1024));
        $pow = min($pow, count($units) - 1);
        $bytes /= (1 << (10 * $pow));
        return round($bytes, 2) . ' ' . $units[$pow];
    }

    /**
     * Helper function to parse size string to bytes
     */
    function parse_bytes($value) {
        $value = trim($value);
        if ($value === '-1') {
            return PHP_INT_MAX;
        }
        $unit = strtoupper(substr($value, -1));
        $value = (int) substr($value, 0, -1);
        switch ($unit) {
            case 'G':
                $value *= 1024;
            case 'M':
                $value *= 1024;
            case 'K':
                $value *= 1024;
                break;
        }
        return $value;
    }
    ?>
</body>
</html>
