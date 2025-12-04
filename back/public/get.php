<?php

declare(strict_types=1);

use Dotenv\Dotenv;
use App\Models\Client;
use App\Models\Source;
use App\Models\Filter;
use App\Services\FilterService;
use App\Models\LiveStream;
use App\Models\VodStream;
use App\Models\Series;

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
$type = $_GET['type'] ?? 'live'; // live, vod, series

if (!$username || !$password) {
    http_response_code(401);
    echo 'Unauthorized: Missing credentials';
    exit;
}

// Authenticate client
$clients = Client::findAll(['username' => $username, 'password' => $password]);
if (empty($clients)) {
    http_response_code(401);
    echo 'Unauthorized: Invalid credentials';
    exit;
}

$client = $clients[0];

// Check if client has source
if (!$client->source_id) {
    http_response_code(400);
    echo 'Bad Request: No source assigned';
    exit;
}

// Load source and filter
$source = Source::find($client->source_id);
if (!$source) {
    http_response_code(404);
    echo 'Not Found: Source not found';
    exit;
}

$filter = null;
if ($client->filter_id) {
    $filter = Filter::find($client->filter_id);
}

// Get proxy URL
$proxyUrl = $_ENV['APP_URL'] ??
            ($_SERVER['REQUEST_SCHEME'] ?? 'http') . '://' . ($_SERVER['HTTP_HOST'] ?? 'localhost');

// Generate M3U playlist
header('Content-Type: application/vnd.apple.mpegurl; charset=utf-8');
header('Content-Disposition: attachment; filename="playlist.m3u8"');

echo "#EXTM3U\n";

// Get streams based on type
$streams = match ($type) {
    'live' => LiveStream::getBySource($source->id, false),
    'vod' => VodStream::getBySource($source->id, false),
    'series' => Series::getBySource($source->id, false),
    default => [],
};

// Apply filtering if filter assigned
if ($filter !== null) {
    $filterService = new FilterService($filter, (bool) $client->hide_adult_content);
    $streams = $filterService->applyToStreams($streams);
}

// Output M3U entries
foreach ($streams as $stream) {
    $streamUrl = $proxyUrl . '/stream/' . $stream->id . '?username=' . urlencode($username) . '&password=' . urlencode($password);

    echo "#EXTINF:-1";

    // Add metadata if available
    if (isset($stream->category_id)) {
        echo ' tvg-id="' . htmlspecialchars((string) $stream->category_id) . '"';
    }
    if (isset($stream->logo)) {
        echo ' tvg-logo="' . htmlspecialchars($stream->logo) . '"';
    }
    echo ' group-title="' . htmlspecialchars($stream->category_name ?? 'Uncategorized') . '"';
    echo ',' . htmlspecialchars($stream->name ?? '') . "\n";

    echo $streamUrl . "\n";
}
