<?php

declare(strict_types=1);

use Dotenv\Dotenv;
use App\Models\Client;
use App\Models\Source;
use App\Models\Filter;
use App\Services\FilterService;
use App\Services\ContentFilterService;
use App\Controllers\Xtream\XtreamController;
use App\Middleware\ClientAuthMiddleware;
use Psr\Http\Message\ServerRequestInterface;
use Psr\Http\Message\ResponseInterface;
use Slim\Psr7\Request;
use Slim\Psr7\Response;
use Slim\Psr7\Factory\UriFactory;
use Slim\Psr7\Headers;
use Slim\Psr7\Stream;

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
$action = $_GET['action'] ?? null;

// Check if credentials are provided
if (empty($username) || empty($password)) {
    http_response_code(401);
    header('Content-Type: application/json');
    echo json_encode([
        'error' => 'Missing username or password',
        'status' => 'unauthorized',
    ]);
    exit;
}

// Authenticate client using the dedicated authenticate method
$client = Client::authenticate($username, $password);

if ($client === null) {
    // Try to find client by username to provide better error message
    $allClients = Client::findAll();
    $testClient = null;

    foreach ($allClients as $c) {
        if ($c->username === $username) {
            $testClient = $c;
            break;
        }
    }

    if ($testClient === null) {
        $errorMsg = 'Invalid username';
    } elseif ($testClient->password !== $password) {
        $errorMsg = 'Invalid password';
    } elseif (!$testClient->is_active) {
        $errorMsg = 'Client is inactive';
    } elseif ($testClient->expiry_date !== null && new \DateTime() > new \DateTime($testClient->expiry_date)) {
        $errorMsg = 'Client subscription expired';
    } else {
        $errorMsg = 'Authentication failed';
    }

    http_response_code(401);
    header('Content-Type: application/json');
    echo json_encode([
        'error' => $errorMsg,
        'status' => 'unauthorized',
    ]);
    exit;
}

// Check if client has source assignment
if (empty($client->source_id)) {
    http_response_code(401);
    header('Content-Type: application/json');
    echo json_encode([
        'error' => 'No source assigned to client',
        'status' => 'unauthorized',
    ]);
    exit;
}

// Load associated Source model
$source = Source::find($client->source_id);
if ($source === null) {
    http_response_code(404);
    header('Content-Type: application/json');
    echo json_encode([
        'error' => 'Source not found',
    ]);
    exit;
}

// Load associated Filter model (if any)
$filter = null;
if ($client->filter_id !== null) {
    $filter = Filter::find($client->filter_id);
}

// Create PSR-7 request and response objects
$method = $_SERVER['REQUEST_METHOD'] ?? 'GET';

// Build URI
$scheme = $_SERVER['REQUEST_SCHEME'] ?? 'http';
$host = $_SERVER['HTTP_HOST'] ?? 'localhost';
$path = $_SERVER['REQUEST_URI'] ?? '/';
$uri = $scheme . '://' . $host . $path;

// Create URI object
$uriFactory = new UriFactory();
$uriObject = $uriFactory->createUri($uri);

// Get headers - handle both Apache and nginx
$headersArray = [];
if (function_exists('getallheaders')) {
    $headersArray = getallheaders();
} else {
    // Fallback for nginx and other servers
    foreach ($_SERVER as $key => $value) {
        if (strpos($key, 'HTTP_') === 0) {
            $headerName = str_replace('HTTP_', '', $key);
            $headerName = str_replace('_', '-', $headerName);
            $headerName = ucwords(strtolower($headerName), '-');
            $headersArray[$headerName] = $value;
        }
    }
}

// Convert headers array to Headers object
$headers = new Headers($headersArray);

// Create body stream
$body = new Stream(fopen('php://input', 'r'));

// Parse cookies from headers
$cookies = [];
if (isset($headersArray['Cookie'])) {
    $cookieString = $headersArray['Cookie'];
    foreach (explode(';', $cookieString) as $cookie) {
        $parts = explode('=', trim($cookie), 2);
        if (count($parts) === 2) {
            $cookies[$parts[0]] = $parts[1];
        }
    }
}

// Create request and response with proper parameter order
// Request($method, $uri, $headers, $cookies, $serverParams, $body, $protocol)
$request = new Request(
    $method,
    $uriObject,
    $headers,
    $cookies,
    $_SERVER,
    $body
);
$response = new Response();

// Add client, source, and filter to request attributes
$request = $request
    ->withAttribute('client', $client)
    ->withAttribute('source', $source)
    ->withAttribute('filter', $filter)
    ->withAttribute('hide_adult_content', (bool) $client->hide_adult_content);

// Log the connection
$authService = new \App\Services\AuthService();
$authService->logConnection($client, $action ?? 'authenticate', $request);

// Initialize controller
$controller = new XtreamController();

// Route based on action
try {
    switch ($action) {
        // Live Streams
        case 'get_live_categories':
            $response = $controller->getLiveCategories($request, $response);
            break;

        case 'get_live_streams':
            $response = $controller->getLiveStreams($request, $response);
            break;

        // VOD (Movies)
        case 'get_vod_categories':
            $response = $controller->getVodCategories($request, $response);
            break;

        case 'get_vod_streams':
            $response = $controller->getVodStreams($request, $response);
            break;

        case 'get_vod_info':
            $response = $controller->getVodInfo($request, $response);
            break;

        // Series
        case 'get_series_categories':
            $response = $controller->getSeriesCategories($request, $response);
            break;

        case 'get_series':
            $response = $controller->getSeries($request, $response);
            break;

        case 'get_series_info':
            $response = $controller->getSeriesInfo($request, $response);
            break;

        // EPG Data
        case 'get_short_epg':
            $response = $controller->getShortEpg($request, $response);
            break;

        case 'get_simple_data_table':
            $response = $controller->getSimpleDataTable($request, $response);
            break;

        case 'get_xmltv':
            $response = $controller->getXmltv($request, $response);
            break;

        default:
            // No action = authenticate / server info
            $response = $controller->authenticate($request, $response);
            break;
    }

    // Send response headers
    http_response_code($response->getStatusCode());
    foreach ($response->getHeaders() as $name => $values) {
        foreach ($values as $value) {
            header(sprintf('%s: %s', $name, $value), false);
        }
    }

    // Send response body
    echo $response->getBody();
} catch (\Exception $e) {
    http_response_code(500);
    header('Content-Type: application/json');
    echo json_encode([
        'error' => 'Internal server error',
        'message' => $_ENV['APP_DEBUG'] ? $e->getMessage() : 'An error occurred',
    ]);
    exit;
}
