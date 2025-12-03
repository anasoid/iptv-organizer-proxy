<?php

declare(strict_types=1);

use Dotenv\Dotenv;
use Slim\Factory\AppFactory;
use App\Middleware\CorsMiddleware;
use App\Middleware\AdminAuthMiddleware;
use App\Controllers\Admin\AuthController;
use App\Controllers\Admin\SourceController;
use App\Controllers\Admin\ClientController;
use App\Controllers\Admin\FilterController;
use App\Controllers\Admin\AdminUserController;
use App\Controllers\Admin\DashboardController;

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

// Protected admin API routes
$app->group('/api', function ($group) {
    // Source management
    $sourceController = new SourceController();
    $group->get('/sources', [$sourceController, 'list']);
    $group->post('/sources', [$sourceController, 'create']);
    $group->get('/sources/{id}', [$sourceController, 'get']);
    $group->put('/sources/{id}', [$sourceController, 'update']);
    $group->delete('/sources/{id}', [$sourceController, 'delete']);
    $group->post('/sources/{id}/test', [$sourceController, 'testConnection']);
    $group->post('/sources/{id}/sync', [$sourceController, 'sync']);
    $group->get('/sources/{id}/sync-logs', [$sourceController, 'syncLogs']);
    $group->post('/sources/{id}/sync/{taskType}', [$sourceController, 'syncTaskType']);
    $group->post('/sources/{id}/sync-all', [$sourceController, 'syncAll']);

    // Client management
    $clientController = new ClientController();
    $group->get('/clients', [$clientController, 'list']);
    $group->post('/clients', [$clientController, 'create']);
    $group->get('/clients/{id}', [$clientController, 'get']);
    $group->put('/clients/{id}', [$clientController, 'update']);
    $group->delete('/clients/{id}', [$clientController, 'delete']);
    $group->get('/clients/{id}/logs', [$clientController, 'logs']);
    $group->get('/clients/{id}/export/live-categories', [$clientController, 'exportLiveCategories']);
    $group->get('/clients/{id}/export/vod-categories', [$clientController, 'exportVodCategories']);
    $group->get('/clients/{id}/export/series-categories', [$clientController, 'exportSeriesCategories']);
    $group->get('/clients/{id}/export/live-streams', [$clientController, 'exportLiveStreams']);
    $group->get('/clients/{id}/export/vod-streams', [$clientController, 'exportVodStreams']);
    $group->get('/clients/{id}/export/series', [$clientController, 'exportSeries']);
    $group->get('/clients/{id}/export/blocked-live-categories', [$clientController, 'exportBlockedLiveCategories']);
    $group->get('/clients/{id}/export/blocked-vod-categories', [$clientController, 'exportBlockedVodCategories']);
    $group->get('/clients/{id}/export/blocked-series-categories', [$clientController, 'exportBlockedSeriesCategories']);
    $group->get('/clients/{id}/export/blocked-live-streams', [$clientController, 'exportBlockedLiveStreams']);
    $group->get('/clients/{id}/export/blocked-vod-streams', [$clientController, 'exportBlockedVodStreams']);
    $group->get('/clients/{id}/export/blocked-series', [$clientController, 'exportBlockedSeries']);

    // Filter management
    $filterController = new FilterController();
    $group->get('/filters', [$filterController, 'list']);
    $group->post('/filters', [$filterController, 'create']);
    $group->get('/filters/{id}', [$filterController, 'get']);
    $group->put('/filters/{id}', [$filterController, 'update']);
    $group->delete('/filters/{id}', [$filterController, 'delete']);
    $group->post('/filters/{id}/preview', [$filterController, 'preview']);

    // Admin user management
    $adminUserController = new AdminUserController();
    $group->get('/admin-users', [$adminUserController, 'list']);
    $group->post('/admin-users', [$adminUserController, 'create']);
    $group->get('/admin-users/{id}', [$adminUserController, 'get']);
    $group->put('/admin-users/{id}', [$adminUserController, 'update']);
    $group->delete('/admin-users/{id}', [$adminUserController, 'delete']);
    $group->post('/admin-users/{id}/change-password', [$adminUserController, 'changePassword']);

    // Dashboard
    $dashboardController = new DashboardController();
    $group->get('/dashboard/stats', [$dashboardController, 'stats']);
    $group->get('/dashboard/activity', [$dashboardController, 'activity']);
    $group->get('/sync/status', [$dashboardController, 'syncStatus']);
})->add(new AdminAuthMiddleware());

// Run application
$app->run();
