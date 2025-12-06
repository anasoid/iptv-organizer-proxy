<?php

declare(strict_types=1);

use Dotenv\Dotenv;
use App\Models\Client;
use App\Models\Source;

// Determine environment based on directory structure
$vendorPath = __DIR__ . '/../vendor/autoload.php';
if (!file_exists($vendorPath)) {
    $vendorPath = __DIR__ . '/../../vendor/autoload.php';
}
require $vendorPath;

// Load environment variables
$envPath = __DIR__ . '/../..';
if (!file_exists(__DIR__ . '/../../.env')) {
    $envPath = __DIR__ . '/../..';
}
$dotenv = Dotenv::createImmutable($envPath);
$dotenv->load();

// Get credentials from query parameters
$username = $_GET['username'] ?? null;
$password = $_GET['password'] ?? null;

if (!$username || !$password) {
    http_response_code(401);
    echo 'Missing required parameters: username and password';
    exit;
}

// Authenticate client
$clients = Client::findAll(['username' => $username, 'password' => $password]);
if (empty($clients)) {
    http_response_code(401);
    echo 'Invalid credentials';
    exit;
}

$client = $clients[0];

// Check if client has source
if (!$client->source_id) {
    http_response_code(400);
    echo 'No source configured';
    exit;
}

// Load source
$source = Source::find($client->source_id);
if (!$source) {
    http_response_code(404);
    echo 'Source not found';
    exit;
}

// Build timeshift URL with source credentials
try {
    $timeshiftUrl = rtrim($source->url, '/') . '/streaming/timeshift.php?';
    $timeshiftParams = [
        'username' => $source->username,
        'password' => $source->password,
    ];

    // Add all other query parameters as-is (stream, duration, start, etc.)
    foreach ($_GET as $key => $value) {
        if (!in_array($key, ['username', 'password'])) {
            $timeshiftParams[$key] = $value;
        }
    }

    $timeshiftUrl .= http_build_query($timeshiftParams);

    // Redirect to original source with source credentials
    http_response_code(302);
    header('Location: ' . $timeshiftUrl);
    exit;

} catch (\Exception $e) {
    http_response_code(500);
    echo 'Error: ' . htmlspecialchars($e->getMessage());
}
