<?php

declare(strict_types=1);

use Dotenv\Dotenv;
use App\Models\Client;
use App\Models\Source;

// Determine environment based on directory structure
$vendorPath = __DIR__ . '/vendor/autoload.php';
if (!file_exists($vendorPath)) {
    $vendorPath = __DIR__ . '/../vendor/autoload.php';
}
require $vendorPath;

// Load environment variables
$envPath = __DIR__;
if (!file_exists(__DIR__ . '/.env')) {
    $envPath = __DIR__ . '/..';
}
$dotenv = Dotenv::createImmutable($envPath);
$dotenv->load();

// Get credentials from query parameters
$username = $_GET['username'] ?? null;
$password = $_GET['password'] ?? null;

if (!$username || !$password) {
    http_response_code(401);
    header('Content-Type: application/xml');
    echo '<?xml version="1.0" encoding="UTF-8"?>' . "\n";
    echo '<tv></tv>';
    exit;
}

// Authenticate client
$clients = Client::findAll(['username' => $username, 'password' => $password]);
if (empty($clients)) {
    http_response_code(401);
    header('Content-Type: application/xml');
    echo '<?xml version="1.0" encoding="UTF-8"?>' . "\n";
    echo '<tv></tv>';
    exit;
}

$client = $clients[0];

// Check if client has source
if (!$client->source_id) {
    http_response_code(400);
    header('Content-Type: application/xml');
    echo '<?xml version="1.0" encoding="UTF-8"?>' . "\n";
    echo '<tv></tv>';
    exit;
}

// Load source
$source = Source::find($client->source_id);
if (!$source) {
    http_response_code(404);
    header('Content-Type: application/xml');
    echo '<?xml version="1.0" encoding="UTF-8"?>' . "\n";
    echo '<tv></tv>';
    exit;
}

// Proxy XMLTV from original source
try {
    $xtreamClient = new \App\Services\Xtream\XtreamClient($source);
    $xmltvResponse = $xtreamClient->getEpgClient()->getXmltv();

    header('Content-Type: application/xml; charset=utf-8');

    // Stream the response body directly without loading into memory
    echo $xmltvResponse->getBody();
} catch (\Exception $e) {
    http_response_code(500);
    header('Content-Type: application/xml');
    echo '<?xml version="1.0" encoding="UTF-8"?>' . "\n";
    echo '<tv><!-- Error: ' . htmlspecialchars($e->getMessage()) . ' --></tv>';
}

