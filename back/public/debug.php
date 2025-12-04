<?php

declare(strict_types=1);

use Dotenv\Dotenv;
use App\Models\Client;

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

// Check if debug is enabled
if (!($_ENV['APP_DEBUG'] ?? false)) {
    http_response_code(403);
    echo 'Debug mode is disabled';
    exit;
}

header('Content-Type: application/json');

// Get all clients
$clients = Client::findAll();

if (empty($clients)) {
    echo json_encode([
        'message' => 'No clients found in database',
        'clients' => [],
    ]);
    exit;
}

// Format client data
$clientData = [];
foreach ($clients as $client) {
    $clientData[] = [
        'id' => $client->id,
        'username' => $client->username,
        'password' => $client->password,
        'source_id' => $client->source_id,
        'filter_id' => $client->filter_id,
        'is_active' => (bool) $client->is_active,
        'expiry_date' => $client->expiry_date,
        'email' => $client->email,
    ];
}

echo json_encode([
    'total' => count($clientData),
    'clients' => $clientData,
], JSON_PRETTY_PRINT);
