<?php

declare(strict_types=1);

use Dotenv\Dotenv;
use Slim\Factory\AppFactory;

require __DIR__ . '/../vendor/autoload.php';

// Load environment variables
$dotenv = Dotenv::createImmutable(__DIR__ . '/..');
$dotenv->load();

// Create Slim application
$app = AppFactory::create();

// Add error middleware
$app->addErrorMiddleware(
    (bool) ($_ENV['APP_DEBUG'] ?? true),
    true,
    true
);

// Define routes
$app->get('/', function ($request, $response) {
    $response->getBody()->write('IPTV Organizer Proxy API');
    return $response;
});

// Health check endpoint
$app->get('/health', function ($request, $response) {
    $response->getBody()->write(json_encode([
        'status' => 'ok',
        'timestamp' => time(),
    ]));
    return $response->withHeader('Content-Type', 'application/json');
});

// Run application
$app->run();
