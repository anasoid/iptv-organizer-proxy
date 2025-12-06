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

// Check if we should redirect instead of proxy
$useRedirect = filter_var($_ENV['XMLTV_USE_REDIRECT'] ?? false, FILTER_VALIDATE_BOOLEAN);

if ($useRedirect) {
    // Redirect to source XMLTV with source credentials
    $xmltvUrl = rtrim($source->url, '/') . '/xmltv.php?username=' . urlencode($source->username) . '&password=' . urlencode($source->password);
    http_response_code(302);
    header('Location: ' . $xmltvUrl);
    exit;
}

// Proxy XMLTV from original source
try {
    $xtreamClient = new \App\Services\Xtream\XtreamClient($source);
    $xmltvResponse = $xtreamClient->getEpgClient()->getXmltv();

    header('Content-Type: application/xml; charset=utf-8');

    // Stream the response body directly in chunks without loading entire file into memory
    $body = $xmltvResponse->getBody();
    while (!$body->eof()) {
        echo $body->read(8192); // Read and output 8KB chunks
    }
} catch (\Exception $e) {
    http_response_code(500);
    header('Content-Type: application/xml');
    echo '<?xml version="1.0" encoding="UTF-8"?>' . "\n";
    echo '<tv><!-- Error: ' . htmlspecialchars($e->getMessage()) . ' --></tv>';
}

