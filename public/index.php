<?php

declare(strict_types=1);

use Dotenv\Dotenv;
use Slim\Factory\AppFactory;
use App\Middleware\CorsMiddleware;
use App\Middleware\ClientAuthMiddleware;
use App\Middleware\AdminAuthMiddleware;
use App\Controllers\XtreamController;
use App\Controllers\AuthController;

require __DIR__ . '/../vendor/autoload.php';

// Load environment variables
$dotenv = Dotenv::createImmutable(__DIR__ . '/..');
$dotenv->load();

// Create Slim application
$app = AppFactory::create();

// Add CORS middleware globally
$app->add(new CorsMiddleware());

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

// Admin authentication API routes
$authController = new AuthController();
$app->post('/api/auth/login', [$authController, 'login']);
$app->post('/api/auth/logout', [$authController, 'logout'])->add(new AdminAuthMiddleware());
$app->get('/api/auth/me', [$authController, 'getCurrentUser'])->add(new AdminAuthMiddleware());

// Xtream Codes API routes (protected by ClientAuthMiddleware)
$app->group('/player_api.php', function ($group) {
    $controller = new XtreamController();

    // Live streams endpoints
    $group->get('[/]', function ($request, $response) use ($controller) {
        $queryParams = $request->getQueryParams();
        $action = $queryParams['action'] ?? null;

        switch ($action) {
            case 'get_live_categories':
                return $controller->getLiveCategories($request, $response);

            case 'get_live_streams':
                return $controller->getLiveStreams($request, $response);

            case 'get_vod_categories':
                return $controller->getVodCategories($request, $response);

            case 'get_series_categories':
                return $controller->getSeriesCategories($request, $response);

            default:
                // No action = authenticate
                return $controller->authenticate($request, $response);
        }
    });
})->add(new ClientAuthMiddleware());

// Run application
$app->run();
