<?php

/**
 * Test Stream Endpoint Debug Script
 *
 * Usage: php test_stream_endpoint.php
 * This script tests the /movie endpoint and shows detailed debugging info
 */

// Configuration
$baseUrl = 'http://localhost:8080';
$username = 'aa';
$password = 'bb';
$streamId = 1335584;
$ext = 'mp4';

$testUrl = "{$baseUrl}/movie/{$username}/{$password}/{$streamId}.{$ext}";

echo "=== Stream Endpoint Debug Test ===\n\n";
echo "Test URL: {$testUrl}\n\n";

// Test 1: Check if URL is reachable
echo "Test 1: Checking if endpoint is reachable...\n";
$ch = curl_init($testUrl);
curl_setopt($ch, CURLOPT_RETURNTRANSFER, true);
curl_setopt($ch, CURLOPT_HEADER, true);
curl_setopt($ch, CURLOPT_TIMEOUT, 10);
curl_setopt($ch, CURLOPT_FOLLOWLOCATION, false);
curl_setopt($ch, CURLOPT_SSL_VERIFYPEER, false);
curl_setopt($ch, CURLOPT_SSL_VERIFYHOST, false);

$response = curl_exec($ch);
$httpCode = curl_getinfo($ch, CURLINFO_HTTP_CODE);
$curlErrno = curl_errno($ch);
$curlError = curl_error($ch);

if ($curlErrno !== 0) {
    echo "❌ cURL Error: [$curlErrno] {$curlError}\n";
} else {
    echo "✅ cURL connection successful\n";
}
echo "   HTTP Status Code: {$httpCode}\n\n";

// Test 2: Parse response
echo "Test 2: Analyzing response...\n";
$headerSize = curl_getinfo($ch, CURLINFO_HEADER_SIZE);
$headers = substr($response, 0, $headerSize);
$body = substr($response, $headerSize);

echo "Headers:\n";
echo "--------\n";
foreach (explode("\n", $headers) as $header) {
    $header = trim($header);
    if (!empty($header)) {
        echo "  {$header}\n";
    }
}
echo "\n";

echo "Body (first 500 chars):\n";
echo "-------\n";
echo substr($body, 0, 500) . "\n";
if (strlen($body) > 500) {
    echo "  ... (truncated, total: " . strlen($body) . " bytes) ...\n";
}
echo "\n";

// Test 3: Analyze status code
echo "Test 3: Status Code Analysis\n";
echo "---------\n";

switch ($httpCode) {
    case 200:
        echo "✅ 200 OK - Stream endpoint working\n";
        break;
    case 301:
    case 302:
        echo "⚠️  {$httpCode} Redirect - Check Location header\n";
        break;
    case 400:
        echo "❌ 400 Bad Request - Check parameters\n";
        break;
    case 401:
        echo "❌ 401 Unauthorized - Invalid credentials (username/password)\n";
        break;
    case 403:
        echo "❌ 403 Forbidden - Access denied\n";
        break;
    case 404:
        echo "❌ 404 Not Found - Endpoint or resource not found\n";
        break;
    case 500:
        echo "❌ 500 Internal Server Error - Server error (check logs)\n";
        break;
    default:
        echo "⚠️  {$httpCode} - Unknown status\n";
}
echo "\n";

curl_close($ch);

// Test 4: Database check (if available)
echo "Test 4: Database Check\n";
echo "----------\n";

$envPath = __DIR__;
if (!file_exists(__DIR__ . '/.env')) {
    $envPath = __DIR__ . '/..';
}

if (file_exists($envPath . '/.env')) {
    echo "✅ .env file found at: {$envPath}/.env\n";

    // Load env
    $dotenv = Dotenv\Dotenv::createImmutable($envPath);
    try {
        $dotenv->load();
        echo "✅ Environment variables loaded\n\n";

        // Try to check database
        try {
            require_once 'vendor/autoload.php';

            $dbType = $_ENV['DB_TYPE'] ?? 'sqlite';
            echo "Database Type: {$dbType}\n";

            if ($dbType === 'mysql') {
                echo "MySQL Configuration:\n";
                echo "  Host: {$_ENV['DB_HOST']}\n";
                echo "  Port: {$_ENV['DB_PORT']}\n";
                echo "  Name: {$_ENV['DB_NAME']}\n";
                echo "  User: {$_ENV['DB_USER']}\n";
            } else {
                echo "SQLite Configuration:\n";
                echo "  Path: {$_ENV['DB_SQLITE_PATH']}\n";
            }

            echo "\n⚠️  Note: Actual database queries require full application bootstrap\n";

        } catch (\Exception $e) {
            echo "⚠️  Could not load database config: {$e->getMessage()}\n";
        }
    } catch (\Exception $e) {
        echo "❌ Error loading .env: {$e->getMessage()}\n";
    }
} else {
    echo "❌ .env file not found\n";
}

echo "\n=== Debug Complete ===\n";
echo "\nNext Steps:\n";
echo "1. Run: php test_stream_endpoint.php\n";
echo "2. Check the HTTP status code above\n";
echo "3. Share the output with the error details\n";
